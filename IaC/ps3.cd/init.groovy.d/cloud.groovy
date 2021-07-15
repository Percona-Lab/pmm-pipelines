import com.amazonaws.services.ec2.model.InstanceType
import hudson.model.*
import hudson.plugins.ec2.AmazonEC2Cloud
import hudson.plugins.ec2.EC2Tag
import hudson.plugins.ec2.SlaveTemplate
import hudson.plugins.ec2.SpotConfiguration
import hudson.plugins.ec2.ConnectionStrategy
import hudson.plugins.ec2.HostKeyVerificationStrategyEnum
import hudson.plugins.ec2.UnixData
import java.util.logging.Logger
import jenkins.model.Jenkins

System.setProperty("hudson.model.DirectoryBrowserSupport.CSP", "sandbox allow-scripts; script-src 'unsafe-inline' https://www.gstatic.com;")

def logger = Logger.getLogger("")
logger.info("Cloud init started")

// get Jenkins instance
Jenkins jenkins = Jenkins.getInstance()

netMap = [:]
netMap['eu-west-1b'] = 'subnet-06b7b6c7fd86a48e8'
netMap['eu-west-1c'] = 'subnet-0de17643aea1f04a4'

imageMap = [:]
imageMap['eu-west-1a.docker'] = 'ami-058b1b7fe545997ae'
imageMap['eu-west-1a.docker-32gb'] = 'ami-058b1b7fe545997ae'
imageMap['eu-west-1a.docker2'] = 'ami-058b1b7fe545997ae'
imageMap['eu-west-1a.micro-amazon'] = 'ami-058b1b7fe545997ae'
imageMap['eu-west-1a.fips-centos-7-x64'] = 'ami-0ff760d16d9497662'

imageMap['eu-west-1a.min-centos-6-x64'] = 'ami-0451e9d3427711cb1'
imageMap['eu-west-1a.min-centos-7-x64'] = 'ami-04f5641b0d178a27a'
imageMap['eu-west-1a.min-buster-x64']   = 'ami-04e1d2f88740af5e1'
imageMap['eu-west-1a.min-bionic-x64']   = 'ami-0c259a97cbf621daf'
imageMap['eu-west-1a.min-stretch-x64']  = 'ami-097672ef083ca4411'
imageMap['eu-west-1a.min-xenial-x64']   = 'ami-038d7b856fe7557b3'
imageMap['eu-west-1a.docker-32gb-hirsute'] = 'ami-03c54cffe1a147d6c'

imageMap['eu-west-1b.docker'] = imageMap['eu-west-1a.docker']
imageMap['eu-west-1b.docker-32gb'] = imageMap['eu-west-1a.docker-32gb']
imageMap['eu-west-1b.docker2'] = imageMap['eu-west-1a.docker2']
imageMap['eu-west-1b.micro-amazon'] = imageMap['eu-west-1a.micro-amazon']
imageMap['eu-west-1b.min-centos-7-x64'] = imageMap['eu-west-1a.min-centos-7-x64']
imageMap['eu-west-1b.fips-centos-7-x64'] = imageMap['eu-west-1a.fips-centos-7-x64']

imageMap['eu-west-1b.min-centos-6-x64'] = imageMap['eu-west-1a.min-centos-6-x64']
imageMap['eu-west-1b.min-buster-x64']   = imageMap['eu-west-1a.min-buster-x64']
imageMap['eu-west-1b.min-bionic-x64']   = imageMap['eu-west-1a.min-bionic-x64']
imageMap['eu-west-1b.min-stretch-x64']  = imageMap['eu-west-1a.min-stretch-x64']
imageMap['eu-west-1b.min-xenial-x64']   = imageMap['eu-west-1a.min-xenial-x64']
imageMap['eu-west-1b.docker-32gb-hirsute'] = imageMap['eu-west-1a.docker-32gb-hirsute']

imageMap['eu-west-1c.docker'] = imageMap['eu-west-1a.docker']
imageMap['eu-west-1c.docker-32gb'] = imageMap['eu-west-1a.docker-32gb']
imageMap['eu-west-1c.docker2'] = imageMap['eu-west-1a.docker2']
imageMap['eu-west-1c.micro-amazon'] = imageMap['eu-west-1a.micro-amazon']
imageMap['eu-west-1c.min-centos-7-x64'] = imageMap['eu-west-1a.min-centos-7-x64']
imageMap['eu-west-1c.fips-centos-7-x64'] = imageMap['eu-west-1a.fips-centos-7-x64']

imageMap['eu-west-1c.min-centos-6-x64'] = imageMap['eu-west-1a.min-centos-6-x64']
imageMap['eu-west-1c.min-buster-x64']   = imageMap['eu-west-1a.min-buster-x64']
imageMap['eu-west-1c.min-bionic-x64']   = imageMap['eu-west-1a.min-bionic-x64']
imageMap['eu-west-1c.min-stretch-x64']  = imageMap['eu-west-1a.min-stretch-x64']
imageMap['eu-west-1c.min-xenial-x64']   = imageMap['eu-west-1a.min-xenial-x64']
imageMap['eu-west-1c.docker-32gb-hirsute'] = imageMap['eu-west-1a.docker-32gb-hirsute']

priceMap = [:]
priceMap['t2.small'] = '0.01'
priceMap['m1.medium'] = '0.05'
priceMap['c4.xlarge'] = '0.10'
priceMap['m4.xlarge'] = '0.10'
priceMap['m4.2xlarge'] = '0.20'
priceMap['c5d.xlarge'] = '0.20'

userMap = [:]
userMap['docker']            = 'ec2-user'
userMap['docker-32gb']       = userMap['docker']
userMap['docker2']           = userMap['docker']
userMap['micro-amazon']      = userMap['docker']
userMap['min-bionic-x64']    = 'ubuntu'
userMap['min-xenial-x64']    = 'ubuntu'
userMap['min-centos-6-x32']  = 'root'
userMap['min-centos-6-x64']  = 'centos'
userMap['min-centos-7-x64']  = 'centos'
userMap['fips-centos-7-x64'] = 'centos'
userMap['min-stretch-x64']   = 'admin'
userMap['min-buster-x64']    = 'admin'
userMap['docker-32gb-hirsute'] = 'ubuntu'

userMap['psmdb'] = userMap['min-xenial-x64']

initMap = [:]
initMap['docker'] = '''
    set -o xtrace

    if ! mountpoint -q /mnt; then
        for DEVICE_NAME in $(lsblk -ndpbo NAME,SIZE | sort -n -r | awk '{print $1}'); do
            if ! grep -qs "${DEVICE_NAME}" /proc/mounts; then
                DEVICE="${DEVICE_NAME}"
                break
            fi
        done
        if [ -n "${DEVICE}" ]; then
            sudo mkfs.ext4 ${DEVICE}
            sudo mount -o noatime ${DEVICE} /mnt
        fi
    fi
    sudo ethtool -K eth0 sg off
    until sudo yum makecache; do
        sleep 1
        echo try again
    done

    sudo amazon-linux-extras install epel -y
    sudo yum -y install java-1.8.0-openjdk git docker p7zip
    sudo yum -y remove java-1.7.0-openjdk awscli

    if ! $(aws --version | grep -q 'aws-cli/2'); then
        sudo rm -rf /tmp/aws* || true

        until curl "https://awscli.amazonaws.com/awscli-exe-linux-x86_64.zip" -o "/tmp/awscliv2.zip"; do
            sleep 1
            echo try again
        done

        7za -aoa -o/tmp x /tmp/awscliv2.zip 
        cd /tmp/aws && sudo ./install
    fi

    sudo install -o $(id -u -n) -g $(id -g -n) -d /mnt/jenkins

    sudo sysctl net.ipv4.tcp_fin_timeout=15
    sudo sysctl net.ipv4.tcp_tw_reuse=1
    sudo sysctl net.ipv6.conf.all.disable_ipv6=1
    sudo sysctl net.ipv6.conf.default.disable_ipv6=1
    sudo sysctl -w fs.inotify.max_user_watches=10000000 || true
    sudo sysctl -w fs.aio-max-nr=1048576 || true
    sudo sysctl -w fs.file-max=6815744 || true
    echo "*  soft  core  unlimited" | sudo tee -a /etc/security/limits.conf
    sudo sed -i.bak -e 's/nofile=1024:4096/nofile=900000:900000/; s/DAEMON_MAXFILES=.*/DAEMON_MAXFILES=990000/' /etc/sysconfig/docker
    echo 'DOCKER_STORAGE_OPTIONS="--data-root=/mnt/docker"' | sudo tee -a /etc/sysconfig/docker-storage
    sudo sed -i.bak -e 's^ExecStart=.*^ExecStart=/usr/bin/dockerd --data-root=/mnt/docker --default-ulimit nofile=900000:900000^' /usr/lib/systemd/system/docker.service
    sudo systemctl daemon-reload
    sudo install -o root -g root -d /mnt/docker
    sudo usermod -aG docker $(id -u -n)
    sudo mkdir -p /etc/docker
    echo '{"experimental": true}' | sudo tee /etc/docker/daemon.json
    sudo systemctl status docker || sudo systemctl start docker
    sudo service docker status || sudo service docker start
    echo "* * * * * root /usr/sbin/route add default gw 10.177.1.1 eth0" | sudo tee /etc/cron.d/fix-default-route
'''

initMap['docker-32gb-hirsute'] = '''
    set -o xtrace
    if ! mountpoint -q /mnt; then
        for DEVICE_NAME in $(lsblk -ndpbo NAME,SIZE | sort -n -r | awk '{print $1}'); do
            if ! grep -qs "${DEVICE_NAME}" /proc/mounts; then
                DEVICE="${DEVICE_NAME}"
                break
            fi
        done
        if [ -n "${DEVICE}" ]; then
            sudo mkfs.ext2 ${DEVICE}
            sudo mount ${DEVICE} /mnt
        fi
    fi
    until sudo apt-get update; do
        sleep 1
        echo try again
    done

    until sudo apt-get -y install openjdk-8-jre-headless apt-transport-https ca-certificates curl gnupg lsb-release unzip; do
        sleep 1
        echo try again
    done

    curl -fsSL https://download.docker.com/linux/ubuntu/gpg | sudo gpg --dearmor -o /usr/share/keyrings/docker-archive-keyring.gpg
    echo "deb [arch=amd64 signed-by=/usr/share/keyrings/docker-archive-keyring.gpg] https://download.docker.com/linux/ubuntu $(lsb_release -cs) stable" | sudo tee /etc/apt/sources.list.d/docker.list > /dev/null
    until sudo apt-get update; do
        sleep 1
        echo try again
    done
    until sudo apt-get -y install docker-ce docker-ce-cli containerd.io; do
        sleep 1
        echo try again
    done

    if ! $(aws --version | grep -q 'aws-cli/2'); then
        find /tmp -maxdepth 1 -name "*aws*" | xargs sudo rm -rf

        until curl "https://awscli.amazonaws.com/awscli-exe-linux-x86_64.zip" -o "/tmp/awscliv2.zip"; do
            sleep 1
            echo try again
        done

        unzip -o /tmp/awscliv2.zip -d /tmp
        cd /tmp/aws && sudo ./install
    fi

    sudo install -o $(id -u -n) -g $(id -g -n) -d /mnt/jenkins

    sudo sysctl net.ipv4.tcp_fin_timeout=15
    sudo sysctl net.ipv4.tcp_tw_reuse=1
    sudo sysctl net.ipv6.conf.all.disable_ipv6=1
    sudo sysctl net.ipv6.conf.default.disable_ipv6=1
    sudo sysctl -w fs.inotify.max_user_watches=10000000 || true
    sudo sysctl -w fs.aio-max-nr=1048576 || true
    sudo sysctl -w fs.file-max=6815744 || true
    echo "*  soft  core  unlimited" | sudo tee -a /etc/security/limits.conf
    sudo sed -i.bak -e 's^ExecStart=.*^ExecStart=/usr/bin/dockerd --data-root=/mnt/docker --default-ulimit nofile=900000:900000^' /usr/lib/systemd/system/docker.service
    sudo systemctl daemon-reload
    sudo install -o root -g root -d /mnt/docker
    sudo usermod -aG docker $(id -u -n)
    sudo mkdir -p /etc/docker
    echo '{"experimental": true, "ipv6": true, "fixed-cidr-v6": "fd3c:a8b0:18eb:5c06::/64"}' | sudo tee /etc/docker/daemon.json
    sudo systemctl restart docker
    echo "* * * * * root /usr/sbin/route add default gw 10.177.1.1 eth0" | sudo tee /etc/cron.d/fix-default-route
'''

initMap['rpmMap'] = '''
    set -o xtrace
    RHVER=$(rpm --eval %rhel)
    ARCH=$(uname -m)

    if ! mountpoint -q /mnt; then
        for DEVICE_NAME in $(lsblk -ndbo NAME,SIZE | sort -n -r | awk '{print $1}'); do
            if ! grep -qs "${DEVICE_NAME}" /proc/mounts; then
                DEVICE="/dev/${DEVICE_NAME}"
                break
            fi
        done
        if [ -n "${DEVICE}" ]; then
            sudo mkfs.ext2 ${DEVICE}
            sudo mount ${DEVICE} /mnt
        fi
    fi

    if [[ ${RHVER} -eq 6 ]]; then
        sudo curl https://jenkins.percona.com/downloads/cent6/centos6-eol.repo --output /etc/yum.repos.d/CentOS-Base.repo
        until sudo yum makecache; do
            sleep 1
            echo try again
        done
        if [[ ${ARCH} == "x86_64" ]]; then
            PKGLIST="epel-release centos-release-scl"
        else
            PKGLIST="epel-release"
        fi

        until sudo yum -y install ${PKGLIST}; do    
            sleep 1
            echo try again
        done
        sudo rm /etc/yum.repos.d/epel-testing.repo
        sudo curl https://jenkins.percona.com/downloads/cent6/centos6-epel-eol.repo --output /etc/yum.repos.d/epel.repo
        if [[ ${ARCH} == "x86_64" ]]; then
            sudo curl https://jenkins.percona.com/downloads/cent6/centos6-scl-eol.repo --output /etc/yum.repos.d/CentOS-SCLo-scl.repo
            sudo curl https://jenkins.percona.com/downloads/cent6/centos6-scl-rh-eol.repo --output /etc/yum.repos.d/CentOS-SCLo-scl-rh.repo
        fi
    fi

    until sudo yum makecache; do
        sleep 1
        echo try again
    done
    sudo yum -y install java-1.8.0-openjdk git aws-cli || :
    sudo yum -y remove java-1.7.0-openjdk || :
    sudo install -o $(id -u -n) -g $(id -g -n) -d /mnt/jenkins

    # CentOS 6 x32 workarounds
    if [[ ${ARCH} != "x86_64" ]]; then
        echo 'Defaults !requiretty' | sudo tee /etc/sudoers.d/requiretty
        if [ ! -f /mnt/swapfile ]; then
            sudo dd if=/dev/zero of=/mnt/swapfile bs=1024 count=524288
            sudo chown root:root /mnt/swapfile
            sudo chmod 0600 /mnt/swapfile
            sudo mkswap /mnt/swapfile
            sudo swapon /mnt/swapfile
        fi
        sudo /bin/sed -i '/shm/s/defaults/defaults,size=2500M/' /etc/fstab
        sudo umount /dev/shm
        sudo mount /dev/shm
    fi
'''

initMap['debMap'] = '''
    set -o xtrace
    if ! mountpoint -q /mnt; then
        for DEVICE_NAME in $(lsblk -ndpbo NAME,SIZE | sort -n -r | awk '{print $1}'); do
            if ! grep -qs "${DEVICE_NAME}" /proc/mounts; then
                DEVICE="${DEVICE_NAME}"
                break
            fi
        done
        if [ -n "${DEVICE}" ]; then
            sudo mkfs.ext2 ${DEVICE}
            sudo mount ${DEVICE} /mnt
        fi
    fi
    until sudo apt-get update; do
        sleep 1
        echo try again
    done
    until sudo apt-get install -y lsb-release; do
        sleep 1
        echo try again
    done

    DEB_VER=$(lsb_release -sc)

    if [[ ${DEB_VER} == "buster" ]]; then
        JAVA_VER="openjdk-11-jre-headless"
    else
        JAVA_VER="openjdk-8-jre-headless"
    fi

    sudo apt-get -y install ${JAVA_VER} git
    sudo install -o $(id -u -n) -g $(id -g -n) -d /mnt/jenkins
'''

initMap['docker-32gb'] = initMap['docker']
initMap['docker2'] = initMap['docker']

initMap['micro-amazon'] = initMap['rpmMap']
initMap['min-centos-6-x32'] = initMap['rpmMap']
initMap['min-centos-6-x64'] = initMap['rpmMap']
initMap['min-centos-7-x64'] = initMap['rpmMap']
initMap['fips-centos-7-x64'] = initMap['rpmMap']

initMap['min-buster-x64']  = initMap['debMap']
initMap['min-bionic-x64']  = initMap['debMap']
initMap['min-stretch-x64'] = initMap['debMap']
initMap['min-xenial-x64']  = initMap['debMap']

capMap = [:]
capMap['c4.xlarge'] = '60'
capMap['m4.xlarge'] = '60'
capMap['m4.2xlarge'] = '10'
capMap['c5d.xlarge'] = '10'

typeMap = [:]
typeMap['micro-amazon']      = 'c4.xlarge'
typeMap['docker']            = 'c4.xlarge'
typeMap['docker-32gb']       = 'm4.2xlarge'
typeMap['docker2']           = 'c5d.xlarge'
typeMap['min-centos-7-x64']  = typeMap['docker']
typeMap['fips-centos-7-x64'] = typeMap['min-centos-7-x64']
typeMap['min-bionic-x64']    = typeMap['min-centos-7-x64']
typeMap['min-buster-x64']    = typeMap['min-centos-7-x64']
typeMap['min-centos-6-x32']  = 'm1.medium'
typeMap['min-centos-6-x64']  = 'm4.xlarge'
typeMap['min-stretch-x64']   = typeMap['min-centos-7-x64']
typeMap['min-xenial-x64']    = typeMap['min-centos-7-x64']
typeMap['docker-32gb-hirsute'] = 'm4.2xlarge'

execMap = [:]
execMap['docker']            = '1'
execMap['docker-32gb']       = execMap['docker']
execMap['docker2']           = execMap['docker']
execMap['micro-amazon']      = '30'
execMap['min-bionic-x64']    = '1'
execMap['min-centos-6-x32']  = '1'
execMap['min-centos-6-x64']  = '1'
execMap['min-centos-7-x64']  = '1'
execMap['fips-centos-7-x64'] = '1'
execMap['min-stretch-x64']   = '1'
execMap['min-xenial-x64']    = '1'
execMap['min-buster-x64']    = '1'
execMap['docker-32gb-hirsute'] = '1'

devMap = [:]
devMap['docker']            = '/dev/xvda=:8:true:gp2,/dev/xvdd=:80:true:gp2'
devMap['docker2']           = '/dev/xvda=:8:true:gp2,/dev/xvdd=:80:true:gp2'
devMap['docker-32gb']       = devMap['docker']
devMap['micro-amazon']      = devMap['docker']
devMap['min-bionic-x64']    = '/dev/sda1=:8:true:gp2,/dev/sdd=:80:true:gp2'
devMap['min-centos-6-x64']  = devMap['min-bionic-x64']
devMap['min-centos-7-x64']  = devMap['min-bionic-x64']
devMap['fips-centos-7-x64'] = devMap['min-bionic-x64']
devMap['min-jessie-x64']    = devMap['micro-amazon']
devMap['min-stretch-x64']   = 'xvda=:8:true:gp2,xvdd=:80:true:gp2'
devMap['min-xenial-x64']    = devMap['min-bionic-x64']
devMap['min-centos-6-x32']  = '/dev/sda=:8:true:gp2,/dev/sdd=:80:true:gp2'
devMap['min-buster-x64']    = '/dev/xvda=:8:true:gp2,/dev/xvdd=:80:true:gp2'
devMap['docker-32gb-hirsute'] = devMap['docker']

labelMap = [:]
labelMap['docker']            = ''
labelMap['docker-32gb']       = ''
labelMap['docker2']           = 'docker-32gb'
labelMap['micro-amazon']      = 'master'
labelMap['min-bionic-x64']    = 'asan'
labelMap['min-centos-6-x32']  = ''
labelMap['min-centos-6-x64']  = ''
labelMap['min-centos-7-x64']  = ''
labelMap['fips-centos-7-x64'] = ''
labelMap['min-stretch-x64']   = ''
labelMap['min-xenial-x64']    = ''
labelMap['min-buster-x64']    = ''
labelMap['docker-32gb-hirsute'] = ''

// https://github.com/jenkinsci/ec2-plugin/blob/ec2-1.41/src/main/java/hudson/plugins/ec2/SlaveTemplate.java
SlaveTemplate getTemplate(String OSType, String AZ) {
    return new SlaveTemplate(
        imageMap[AZ + '.' + OSType],                // String ami
        '',                                         // String zone
        new SpotConfiguration(true, priceMap[typeMap[OSType]], false, '0'), // SpotConfiguration spotConfig
        'default',                                  // String securityGroups
        '/mnt/jenkins',                             // String remoteFS
        InstanceType.fromValue(typeMap[OSType]),    // InstanceType type
        ( typeMap[OSType].startsWith("c4") || typeMap[OSType].startsWith("m4") || typeMap[OSType].startsWith("c5") || typeMap[OSType].startsWith("m5") ), // boolean ebsOptimized
        OSType + ' ' + labelMap[OSType],            // String labelString
        Node.Mode.NORMAL,                           // Node.Mode mode
        OSType,                                     // String description
        initMap[OSType],                            // String initScript
        '',                                         // String tmpDir
        '',                                         // String userData
        execMap[OSType],                            // String numExecutors
        userMap[OSType],                            // String remoteAdmin
        new UnixData('', '', '', '22'),             // AMITypeData amiType
        '-Xmx512m -Xms512m',                        // String jvmopts
        false,                                      // boolean stopOnTerminate
        netMap[AZ],                                 // String subnetId
        [
            new EC2Tag('Name', 'jenkins-ps3-' + OSType),
            new EC2Tag('iit-billing-tag', 'jenkins-ps3-worker')
        ],                                          // List<EC2Tag> tags
        '3',                                        // String idleTerminationMinutes
        0,                                          // Init minimumNumberOfInstances
        0,                                          // minimumNumberOfSpareInstances
        capMap[typeMap[OSType]],                    // String instanceCapStr
        'arn:aws:iam::119175775298:instance-profile/jenkins-ps3-worker', // String iamInstanceProfile
        true,                                       // boolean deleteRootOnTermination
        false,                                      // boolean useEphemeralDevices
        false,                                      // boolean useDedicatedTenancy
        '',                                         // String launchTimeoutStr
        true,                                       // boolean associatePublicIp
        devMap[OSType],                             // String customDeviceMapping
        true,                                       // boolean connectBySSHProcess
        false,                                      // boolean monitoring
        false,                                      // boolean t2Unlimited
        ConnectionStrategy.PUBLIC_DNS,              // connectionStrategy
        -1,                                         // int maxTotalUses
        null,
        HostKeyVerificationStrategyEnum.OFF,
    )
}

String privateKey = ''
jenkins.clouds.each {
    if (it.hasProperty('cloudName') && it['cloudName'] == 'AWS-Dev b') {
        privateKey = it['privateKey']
    }
}

String sshKeysCredentialsId = '2ab73cff-5575-4ca2-be48-17761b165103'

String region = 'eu-west-1'
('b'..'c').each {
    // https://github.com/jenkinsci/ec2-plugin/blob/ec2-1.41/src/main/java/hudson/plugins/ec2/AmazonEC2Cloud.java
    AmazonEC2Cloud ec2Cloud = new AmazonEC2Cloud(
        "AWS-Dev ${it}",                        // String cloudName
        true,                                   // boolean useInstanceProfileForCredentials
        '',                                     // String credentialsId
        region,                                 // String region
        privateKey,                             // String privateKey
        sshKeysCredentialsId,                   // String sshKeysCredentialsId
        '240',                                   // String instanceCapStr
        [
            getTemplate('docker',             "${region}${it}"),
            getTemplate('docker-32gb',        "${region}${it}"),
            getTemplate('micro-amazon',       "${region}${it}"),
            getTemplate('min-centos-7-x64',   "${region}${it}"),
            getTemplate('fips-centos-7-x64',  "${region}${it}"),
            getTemplate('min-centos-6-x64',   "${region}${it}"),
            getTemplate('min-bionic-x64',     "${region}${it}"),
            getTemplate('min-buster-x64',     "${region}${it}"),
            getTemplate('min-stretch-x64',    "${region}${it}"),
            getTemplate('min-xenial-x64',     "${region}${it}"),
            getTemplate('docker-32gb-hirsute', "${region}${it}"),
        ],                                       // List<? extends SlaveTemplate> templates
        '',
        ''
    )

    // add cloud configuration to Jenkins
    jenkins.clouds.each {
        if (it.hasProperty('cloudName') && it['cloudName'] == ec2Cloud['cloudName']) {
            jenkins.clouds.remove(it)
        }
    }
    jenkins.clouds.add(ec2Cloud)
}

// save current Jenkins state to disk
jenkins.save()

logger.info("Cloud init finished")