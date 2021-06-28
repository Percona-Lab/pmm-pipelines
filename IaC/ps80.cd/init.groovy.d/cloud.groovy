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

def logger = Logger.getLogger("")
logger.info("Cloud init started")

// get Jenkins instance
Jenkins jenkins = Jenkins.getInstance()

netMap = [:]
netMap['us-west-2b'] = 'subnet-0c0b7f0b15c1d68be'
netMap['us-west-2c'] = 'subnet-024be5829372c4f38'

imageMap = [:]
imageMap['us-west-2a.docker']            = 'ami-0a78fb1467aab24f9'
imageMap['us-west-2a.docker-32gb']       = 'ami-0a78fb1467aab24f9'
imageMap['us-west-2a.docker2']           = 'ami-0a78fb1467aab24f9'
imageMap['us-west-2a.micro-amazon']      = 'ami-0a78fb1467aab24f9'
imageMap['us-west-2a.min-amazon-2-x64']  = 'ami-0a78fb1467aab24f9'
imageMap['us-west-2a.min-centos-8-x64']  = 'ami-0155c31ea13d4abd2'
imageMap['us-west-2a.min-centos-7-x64']  = 'ami-0686851c4e7b1a8e1'
imageMap['us-west-2a.fips-centos-7-x64'] = 'ami-036d2cdf95d86d256'
imageMap['us-west-2a.min-centos-6-x64']  = 'ami-0f3565dfdcdbfccfa'
imageMap['us-west-2a.min-buster-x64']    = 'ami-0c7ea5497c02abcaf'
imageMap['us-west-2a.min-focal-x64']     = 'ami-0554443e7f55d3eeb'
imageMap['us-west-2a.min-bionic-x64']    = 'ami-090717c950a5c34d3'
imageMap['us-west-2a.min-stretch-x64']   = 'ami-0ada74f78fa02ad0d'
imageMap['us-west-2a.min-xenial-x64']    = 'ami-014f0ecd3e71df934'

imageMap['us-west-2b.docker']            = imageMap['us-west-2a.docker']
imageMap['us-west-2b.docker-32gb']       = imageMap['us-west-2a.docker-32gb']
imageMap['us-west-2b.docker2']           = imageMap['us-west-2a.docker2']
imageMap['us-west-2b.micro-amazon']      = imageMap['us-west-2a.micro-amazon']
imageMap['us-west-2b.min-amazon-2-x64']  = imageMap['us-west-2a.min-amazon-2-x64']
imageMap['us-west-2b.min-centos-8-x64']  = imageMap['us-west-2a.min-centos-8-x64']
imageMap['us-west-2b.min-centos-7-x64']  = imageMap['us-west-2a.min-centos-7-x64']
imageMap['us-west-2b.fips-centos-7-x64'] = imageMap['us-west-2a.fips-centos-7-x64']
imageMap['us-west-2b.min-centos-6-x64']  = imageMap['us-west-2a.min-centos-6-x64']
imageMap['us-west-2b.min-buster-x64']    = imageMap['us-west-2a.min-buster-x64']
imageMap['us-west-2b.min-focal-x64' ]    = imageMap['us-west-2a.min-focal-x64']
imageMap['us-west-2b.min-bionic-x64']    = imageMap['us-west-2a.min-bionic-x64']
imageMap['us-west-2b.min-stretch-x64']   = imageMap['us-west-2a.min-stretch-x64']
imageMap['us-west-2b.min-xenial-x64']    = imageMap['us-west-2a.min-xenial-x64']

imageMap['us-west-2c.docker']            = imageMap['us-west-2a.docker']
imageMap['us-west-2c.docker-32gb']       = imageMap['us-west-2a.docker-32gb']
imageMap['us-west-2c.docker2']           = imageMap['us-west-2a.docker2']
imageMap['us-west-2c.micro-amazon']      = imageMap['us-west-2a.micro-amazon']
imageMap['us-west-2c.min-amazon-2-x64']  = imageMap['us-west-2a.min-amazon-2-x64']
imageMap['us-west-2c.min-centos-8-x64']  = imageMap['us-west-2a.min-centos-8-x64']
imageMap['us-west-2c.min-centos-7-x64']  = imageMap['us-west-2a.min-centos-7-x64']
imageMap['us-west-2c.fips-centos-7-x64'] = imageMap['us-west-2a.fips-centos-7-x64']
imageMap['us-west-2c.min-centos-6-x64']  = imageMap['us-west-2a.min-centos-6-x64']
imageMap['us-west-2c.min-buster-x64']    = imageMap['us-west-2a.min-buster-x64']
imageMap['us-west-2c.min-focal-x64' ]    = imageMap['us-west-2a.min-focal-x64']
imageMap['us-west-2c.min-bionic-x64']    = imageMap['us-west-2a.min-bionic-x64']
imageMap['us-west-2c.min-stretch-x64']   = imageMap['us-west-2a.min-stretch-x64']
imageMap['us-west-2c.min-xenial-x64']    = imageMap['us-west-2a.min-xenial-x64']

imageMap['us-west-2d.docker']            = imageMap['us-west-2a.docker']
imageMap['us-west-2d.docker-32gb']       = imageMap['us-west-2a.docker-32gb']
imageMap['us-west-2d.docker2']           = imageMap['us-west-2a.docker2']
imageMap['us-west-2d.micro-amazon']      = imageMap['us-west-2a.micro-amazon']
imageMap['us-west-2d.min-amazon-2-x64']  = imageMap['us-west-2a.min-amazon-2-x64']
imageMap['us-west-2d.min-centos-8-x64']  = imageMap['us-west-2a.min-centos-8-x64']
imageMap['us-west-2d.min-centos-7-x64']  = imageMap['us-west-2a.min-centos-7-x64']
imageMap['us-west-2d.fips-centos-7-x64'] = imageMap['us-west-2a.fips-centos-7-x64']
imageMap['us-west-2d.min-centos-6-x64']  = imageMap['us-west-2a.min-centos-6-x64']
imageMap['us-west-2d.min-buster-x64']    = imageMap['us-west-2a.min-buster-x64']
imageMap['us-west-2d.min-focal-x64' ]    = imageMap['us-west-2a.min-focal-x64']
imageMap['us-west-2d.min-bionic-x64']    = imageMap['us-west-2a.min-bionic-x64']
imageMap['us-west-2d.min-stretch-x64']   = imageMap['us-west-2a.min-stretch-x64']
imageMap['us-west-2d.min-xenial-x64']    = imageMap['us-west-2a.min-xenial-x64']

priceMap = [:]
priceMap['t2.small'] = '0.01'
priceMap['m1.medium'] = '0.05'
priceMap['c5.xlarge'] = '0.10'
priceMap['c3.xlarge'] = '0.14'
priceMap['m4.xlarge'] = '0.10'
priceMap['m4.2xlarge'] = '0.20'
priceMap['m5a.2xlarge'] = '0.25'
priceMap['r4.4xlarge'] = '0.38'
priceMap['m5d.2xlarge'] = '0.20'
priceMap['c5d.xlarge'] = '0.20'

userMap = [:]
userMap['docker']            = 'ec2-user'
userMap['docker-32gb']       = userMap['docker']
userMap['docker2']           = userMap['docker']
userMap['micro-amazon']      = userMap['docker']
userMap['min-amazon-2-x64']  = userMap['docker']
userMap['min-focal-x64']     = 'ubuntu'
userMap['min-bionic-x64']    = 'ubuntu'
userMap['min-xenial-x64']    = 'ubuntu'
userMap['min-centos-6-x64']  = 'centos'
userMap['min-centos-7-x64']  = 'centos'
userMap['fips-centos-7-x64'] = 'centos'
userMap['min-centos-8-x64']  = 'centos'
userMap['min-stretch-x64']   = 'admin'
userMap['min-buster-x64']    = 'admin'

userMap['psmdb'] = userMap['min-xenial-x64']

modeMap = [:]
modeMap['docker']            = 'Node.Mode.NORMAL'
modeMap['docker-32gb']       = modeMap['docker']
modeMap['docker2']           = modeMap['docker']
modeMap['micro-amazon']      = modeMap['docker']
modeMap['min-amazon-2-x64']  = modeMap['docker']
modeMap['min-focal-x64']     = 'Node.Mode.EXCLUSIVE'
modeMap['min-bionic-x64']    = modeMap['min-focal-x64']
modeMap['min-xenial-x64']    = modeMap['min-focal-x64']
modeMap['min-centos-6-x64']  = modeMap['min-focal-x64']
modeMap['min-centos-7-x64']  = modeMap['min-focal-x64']
modeMap['min-centos-8-x64']  = modeMap['min-focal-x64']
modeMap['fips-centos-7-x64'] = modeMap['min-focal-x64']
modeMap['min-stretch-x64']   = modeMap['min-focal-x64']
modeMap['min-buster-x64']    = modeMap['min-focal-x64']

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
        find /tmp -maxdepth 1 -name "*aws*" | xargs sudo rm -rf

        until curl "https://awscli.amazonaws.com/awscli-exe-linux-x86_64.zip" -o "/tmp/awscliv2.zip"; do
            sleep 1
            echo try again
        done

        7za -o/tmp x /tmp/awscliv2.zip 
        cd /tmp/aws && sudo ./install
    fi

    sudo install -o $(id -u -n) -g $(id -g -n) -d /mnt/jenkins

    sudo sysctl net.ipv4.tcp_fin_timeout=15
    sudo sysctl net.ipv4.tcp_tw_reuse=1
    sudo sysctl net.ipv4.ip_local_port_range=1024 65535
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
    echo '{"experimental": true, "ipv6": true, "fixed-cidr-v6": "fd3c:a8b0:18eb:5c06::/64"}' | sudo tee /etc/docker/daemon.json
    sudo systemctl status docker || sudo systemctl start docker
    sudo service docker status || sudo service docker start
    echo "* * * * * root /usr/sbin/route add default gw 10.177.1.1 eth0" | sudo tee /etc/cron.d/fix-default-route
'''
initMap['docker-32gb'] = initMap['docker']
initMap['docker2'] = initMap['docker']
initMap['micro-amazon'] = '''
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
    until sudo yum makecache; do
        sleep 1
        echo try again
    done
    sudo yum -y install java-1.8.0-openjdk aws-cli || :
    sudo yum -y install git || :
    sudo yum -y remove java-1.7.0-openjdk || :
    sudo install -o $(id -u -n) -g $(id -g -n) -d /mnt/jenkins
'''
initMap['min-amazon-2-x64'] = initMap['micro-amazon']
initMap['min-centos-6-x64'] = '''
    set -o xtrace
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
    sudo curl https://jenkins.percona.com/downloads/cent6/centos6-eol.repo --output /etc/yum.repos.d/CentOS-Base.repo
    until sudo yum makecache; do
        sleep 1
        echo try again
    done

    until sudo yum -y install epel-release centos-release-scl; do    
        sleep 1
        echo try again
    done
    sudo rm /etc/yum.repos.d/epel-testing.repo
    sudo curl https://jenkins.percona.com/downloads/cent6/centos6-epel-eol.repo --output /etc/yum.repos.d/epel.repo
    sudo curl https://jenkins.percona.com/downloads/cent6/centos6-scl-eol.repo --output /etc/yum.repos.d/CentOS-SCLo-scl.repo
    sudo curl https://jenkins.percona.com/downloads/cent6/centos6-scl-rh-eol.repo --output /etc/yum.repos.d/CentOS-SCLo-scl-rh.repo

    sudo yum -y install java-1.8.0-openjdk aws-cli || :
    sudo yum -y install git || :
    sudo yum -y remove java-1.7.0-openjdk || :
    sudo install -o $(id -u -n) -g $(id -g -n) -d /mnt/jenkins
'''
initMap['min-centos-7-x64'] = initMap['micro-amazon']
initMap['fips-centos-7-x64'] = initMap['micro-amazon']
initMap['min-centos-8-x64'] = initMap['micro-amazon']
initMap['min-bionic-x64'] = '''
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
    sudo apt-get -y install openjdk-8-jre-headless git
    sudo install -o $(id -u -n) -g $(id -g -n) -d /mnt/jenkins
'''
initMap['min-buster-x64'] = '''
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
    sudo apt-get -y install openjdk-11-jre-headless git
    sudo install -o $(id -u -n) -g $(id -g -n) -d /mnt/jenkins
'''
initMap['min-focal-x64'] = initMap['min-bionic-x64']
initMap['min-stretch-x64'] = initMap['min-bionic-x64']
initMap['min-xenial-x64'] = initMap['min-bionic-x64']

capMap = [:]
capMap['c5.xlarge'] = '60'
capMap['c3.xlarge'] = '60'
capMap['m4.xlarge'] = '5'
capMap['m4.2xlarge'] = '40'
capMap['m5a.2xlarge'] = '40'
capMap['r4.4xlarge'] = '40'
capMap['c5d.xlarge'] = '10'

typeMap = [:]
typeMap['micro-amazon']      = 't2.small'
typeMap['docker']            = 'c3.xlarge'
typeMap['docker-32gb']       = 'm5a.2xlarge'
typeMap['docker2']           = 'r4.4xlarge'
typeMap['min-centos-7-x64']  = typeMap['docker']
typeMap['min-centos-8-x64']  = typeMap['docker']
typeMap['fips-centos-7-x64'] = typeMap['docker-32gb']
typeMap['min-focal-x64']     = typeMap['docker']
typeMap['min-bionic-x64']    = typeMap['min-centos-7-x64']
typeMap['min-buster-x64']    = typeMap['min-centos-7-x64']
typeMap['min-centos-6-x64']  = typeMap['docker']
typeMap['min-stretch-x64']   = typeMap['docker']
typeMap['min-xenial-x64']    = typeMap['docker']
typeMap['min-amazon-2-x64']  = typeMap['docker']

execMap = [:]
execMap['docker']            = '1'
execMap['docker-32gb']       = execMap['docker']
execMap['docker2']           = execMap['docker']
execMap['micro-amazon']      = '30'
execMap['min-amazon-2-x64']  = '1'
execMap['min-bionic-x64']    = '1'
execMap['min-focal-x64']     = '1'
execMap['min-centos-6-x64']  = '1'
execMap['min-centos-7-x64']  = '1'
execMap['fips-centos-7-x64'] = '1'
execMap['min-centos-8-x64']  = '1'
execMap['min-stretch-x64']   = '1'
execMap['min-xenial-x64']    = '1'
execMap['min-buster-x64']    = '1'

devMap = [:]
devMap['docker']            = '/dev/xvda=:8:true:gp2,/dev/xvdd=:120:true:gp2'
devMap['docker2']           = '/dev/xvda=:8:true:gp2,/dev/xvdd=:80:true:gp2'
devMap['docker-32gb']       = devMap['docker']
devMap['micro-amazon']      = '/dev/xvda=:30:true:gp2,/dev/xvdd=:80:true:gp2'
devMap['min-amazon-2-x64']  = '/dev/xvda=:30:true:gp2,/dev/xvdd=:120:true:gp2'
devMap['min-bionic-x64']    = '/dev/sda1=:30:true:gp2,/dev/sdd=:120:true:gp2'
devMap['min-focal-x64']     = devMap['min-bionic-x64']
devMap['min-centos-6-x64']  = devMap['min-bionic-x64']
devMap['min-centos-7-x64']  = devMap['min-bionic-x64']
devMap['fips-centos-7-x64'] = devMap['min-bionic-x64']
devMap['min-centos-8-x64']  = '/dev/sda1=:30:true:gp2,/dev/sdd=:80:true:gp2'
devMap['min-stretch-x64']   = 'xvda=:30:true:gp2,xvdd=:120:true:gp2'
devMap['min-xenial-x64']    = devMap['min-bionic-x64']
devMap['min-buster-x64']    = '/dev/xvda=:30:true:gp2,/dev/xvdd=:120:true:gp2'

labelMap = [:]
labelMap['docker']            = ''
labelMap['docker-32gb']       = ''
labelMap['docker2']           = ''
labelMap['micro-amazon']      = 'master'
labelMap['min-amazon-2-x64']  = ''
labelMap['min-bionic-x64']    = 'asan'
labelMap['min-focal-x64']     = ''
labelMap['min-centos-6-x64']  = ''
labelMap['min-centos-7-x64']  = ''
labelMap['fips-centos-7-x64'] = ''
labelMap['min-centos-8-x64']  = ''
labelMap['min-stretch-x64']   = ''
labelMap['min-xenial-x64']    = ''
labelMap['min-buster-x64']    = ''

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
            new EC2Tag('Name', 'jenkins-ps80-' + OSType),
            new EC2Tag('iit-billing-tag', 'jenkins-ps80-worker')
        ],                                          // List<EC2Tag> tags
        '3',                                        // String idleTerminationMinutes
        0,                                          // Init minimumNumberOfInstances
        0,                                          // minimumNumberOfSpareInstances
        capMap[typeMap[OSType]],                    // String instanceCapStr
        'arn:aws:iam::119175775298:instance-profile/jenkins-ps80-worker', // String iamInstanceProfile
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

String sshKeysCredentialsId = '8af5d00a-aeaf-45bd-a5b1-4a7680c9d500'

String region = 'us-west-2'
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
            getTemplate('min-amazon-2-x64',   "${region}${it}"),
            getTemplate('min-centos-8-x64',   "${region}${it}"),
            getTemplate('min-centos-7-x64',   "${region}${it}"),
            getTemplate('fips-centos-7-x64',  "${region}${it}"),
            getTemplate('min-centos-6-x64',   "${region}${it}"),
            getTemplate('min-bionic-x64',     "${region}${it}"),
            getTemplate('min-focal-x64',      "${region}${it}"),
            getTemplate('min-buster-x64',     "${region}${it}"),
            getTemplate('min-stretch-x64',    "${region}${it}"),
            getTemplate('min-xenial-x64',     "${region}${it}"),
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
