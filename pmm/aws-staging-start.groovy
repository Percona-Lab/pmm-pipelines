import hudson.model.Node.Mode
import hudson.slaves.*
import jenkins.model.Jenkins
import hudson.plugins.sshslaves.SSHLauncher

library changelog: false, identifier: 'lib@master', retriever: modernSCM([
    $class: 'GitSCMSource',
    remote: 'https://github.com/Percona-Lab/jenkins-pipelines.git'
]) _

pipeline {
    agent {
        label 'awscli'
    }
    parameters {
        string(
            defaultValue: 'public.ecr.aws/e7j3v3n0/pmm-server:dev-latest',
            description: 'PMM Server docker container version (image-name:version-tag ex. public.ecr.aws/e7j3v3n0/pmm-server:dev-latest or perconalab/pmm-server:pmm1-dev-latest)',
            name: 'DOCKER_VERSION')
        string(
            defaultValue: 'dev-latest',
            description: 'PMM Client version ("dev-latest" for master branch, "pmm1-dev-latest" for 1.x latest, "latest" or "X.X.X" for released version, "http://..." for feature build)',
            name: 'CLIENT_VERSION')
        string(
            defaultValue: '',
            description: 'public ssh key for "ec2-user" user, please set if you need ssh access',
            name: 'SSH_KEY')
        choice(
            choices: ['pmm2', 'pmm1'],
            description: 'Which Version of PMM-Server',
            name: 'PMM_VERSION')
        choice(
            choices: ['no', 'yes'],
            description: 'Enable Testing Repo, for RC testing',
            name: 'ENABLE_TESTING_REPO')
        choice(
            choices: ['yes', 'no'],
            description: 'Enable Experimental Repo, for dev-latest',
            name: 'ENABLE_EXPERIMENTAL_REPO')
        choice(
            choices: ['no', 'yes'],
            description: 'Enable Push Mode, if you are using this instance as Client Node',
            name: 'ENABLE_PUSH_MODE')
        choice(
            choices: '1\n0\n1\n2\n3\n4\n5\n6\n7\n8\n9\n10\n11\n12\n13\n14\n15\n16\n17\n18\n19\n20\n21\n22\n23\n24\n25\n26\n27\n28\n29\n30',
            description: 'Stop the instance after, days ("0" value disables autostop and recreates instance in case of AWS failure)',
            name: 'DAYS')
        choice(
            choices: ['8.0','5.7'],
            description: 'Percona XtraDB Cluster version',
            name: 'PXC_VERSION')
        choice(
            choices: ['8.0', '5.7', '5.7.30', '5.6'],
            description: "Percona Server for MySQL version",
            name: 'PS_VERSION')
        choice(
            choices: ['5.7', '8.0', '5.6'],
            description: 'MySQL Community Server version',
            name: 'MS_VERSION')
        choice(
            choices: ['13', '12', '11', '10.8'],
            description: "Which version of PostgreSQL",
            name: 'PGSQL_VERSION')
        choice(
            choices: ['13.3', '12.7', '11.12'],
            description: 'Percona Distribution for PostgreSQL',
            name: 'PDPGSQL_VERSION')
        choice(
            choices: ['10.5', '10.4', '10.3', '10.2'],
            description: "MariaDB Server version",
            name: 'MD_VERSION')
        choice(
            choices: ['4.2', '4.4', '4.0', '3.6'],
            description: "Percona Server for MongoDB version",
            name: 'MO_VERSION')
        choice(
            choices: ['4.4', '4.2', '4.0'],
            description: "Official MongoDB version from MongoDB Inc",
            name: 'MODB_VERSION')
        choice(
            choices: ['perfschema', 'slowlog'],
            description: "Query Source for Monitoring",
            name: 'QUERY_SOURCE')
        text(
            defaultValue: '',
            description: '''
            Passing Env Variables to PMM Server Docker Container, supported only for pmm2.x
            An Example: -e PERCONA_TEST_CHECKS_INTERVAL=10s -e PMM_DEBUG=1
            ''',
            name: 'DOCKER_ENV_VARIABLE')
        text(
            defaultValue: '--addclient=ps,1',
            description: '''
            Configure PMM Clients
            ms - MySQL (ex. --addclient=ms,1),
            ps - Percona Server for MySQL (ex. --addclient=ps,1),
            pxc - Percona XtraDB Cluster, --with-proxysql (to be used with proxysql only ex. --addclient=pxc,1 --with-proxysql),
            md - MariaDB Server (ex. --addclient=md,1),
            mo - Percona Server for MongoDB(ex. --addclient=mo,1),
            modb - Official MongoDB version from MongoDB Inc (ex. --addclient=modb,1),
            pgsql - Postgre SQL Server (ex. --addclient=pgsql,1)
            pdpgsql - Percona Distribution for PostgreSQL (ex. --addclient=pdpgsql,1)
            An example: --addclient=ps,1 --addclient=mo,1 --addclient=md,1 --addclient=pgsql,2 --addclient=modb,2
            ''',
            name: 'CLIENTS')
        string(
            defaultValue: 'true',
            description: 'Enable Slack notification (option for high level pipelines)',
            name: 'NOTIFY')
        choice(
            choices: ['no', 'yes'],
            description: "Use this instance only as a client host",
            name: 'CLIENT_INSTANCE')
        string (
            defaultValue: '0.0.0.0',
            description: 'Please change the default Value for Server Public IP, When you need to use this instance just as client',
            name: 'SERVER_IP')
        string(
            defaultValue: 'master',
            description: 'Tag/Branch for pmm-qa repository',
            name: 'PMM_QA_GIT_BRANCH')
        string(
            defaultValue: '',
            description: 'Commit hash for pmm-qa branch',
            name: 'PMM_QA_GIT_COMMIT_HASH')
    }
    options {
        skipDefaultCheckout()
    }

    stages {
        stage('Prepare') {
            steps {
                deleteDir()
                wrap([$class: 'BuildUser']) {
                    sh """
                        echo "\${BUILD_USER_EMAIL}" > OWNER_EMAIL
                        echo "\${BUILD_USER_EMAIL}" | awk -F '@' '{print \$1}' > OWNER_FULL
                        echo "pmm-\$(cat OWNER_FULL)-\$(date -u '+%Y%m%d%H%M%S')-${BUILD_NUMBER}" \
                            > VM_NAME
                    """
                }
                script {
                    def OWNER = sh(returnStdout: true, script: "cat OWNER_FULL").trim()
                    def OWNER_EMAIL = sh(returnStdout: true, script: "cat OWNER_EMAIL").trim()
                    def OWNER_SLACK = slackUserIdFromEmail(botUser: true, email: "${OWNER_EMAIL}", tokenCredentialId: 'JenkinsCI-SlackBot-v2')

                    echo """
                        DOCKER_VERSION: ${DOCKER_VERSION}
                        CLIENT_VERSION: ${CLIENT_VERSION}
                        PMM_VERSION:    ${PMM_VERSION}
                        PXC_VERSION:    ${PXC_VERSION}
                        PS_VERSION:     ${PS_VERSION}
                        MS_VERSION:     ${MS_VERSION}
                        MD_VERSION:     ${MD_VERSION}
                        MO_VERSION:     ${MO_VERSION}
                        MODB_VERSION:   ${MODB_VERSION}
                        PGSQL_VERSION:  ${PGSQL_VERSION}
                        PDPGSQL_VERSION: ${PDPGSQL_VERSION}
                        QUERY_SOURCE:   ${QUERY_SOURCE}
                        CLIENTS:        ${CLIENTS}
                        OWNER:          ${OWNER}
                    """
                    if ("${NOTIFY}" == "true") {
                        slackSend botUser: true, channel: '#pmm-ci', color: '#FFFF00', message: "[${JOB_NAME}]: build started - ${BUILD_URL}"
                        slackSend botUser: true, channel: "@${OWNER_SLACK}", color: '#FFFF00', message: "[${JOB_NAME}]: build started - ${BUILD_URL}"
                    }
                }
            }
        }

        stage('Run VM') {
            steps {
                launchSpotInstance('t3.large', '0.040', 20)
                withCredentials([sshUserPrivateKey(credentialsId: 'aws-jenkins', keyFileVariable: 'KEY_PATH', passphraseVariable: '', usernameVariable: 'USER')]) {
                    sh """
                        until ssh -i "${KEY_PATH}" -o ConnectTimeout=1 -o StrictHostKeyChecking=no ${USER}@\$(cat IP) 'java -version; sudo yum install -y java-1.8.0-openjdk; sudo /usr/sbin/alternatives --set java /usr/lib/jvm/jre-1.8.0-openjdk.x86_64/bin/java; java -version;' ; do
                            sleep 5
                        done
                    """
                }
                script {
                    env.IP      = sh(returnStdout: true, script: "cat IP").trim()
                    env.VM_NAME = sh(returnStdout: true, script: "cat VM_NAME").trim()

                    SSHLauncher ssh_connection = new SSHLauncher(env.IP, 22, 'aws-jenkins')
                    DumbSlave node = new DumbSlave(env.VM_NAME, "spot instance job", "/home/ec2-user/", "1", Mode.EXCLUSIVE, "", ssh_connection, RetentionStrategy.INSTANCE)

                    Jenkins.instance.addNode(node)
                }
                node(env.VM_NAME){
                    sh """
                        set -o errexit
                        set -o xtrace

                        if [ -n "$SSH_KEY" ]; then
                            echo '$SSH_KEY' >> /home/ec2-user/.ssh/authorized_keys
                        fi

                        sudo yum -y update --security
                        sudo yum -y install https://repo.percona.com/yum/percona-release-1.0-25.noarch.rpm
                        sudo rpm --import /etc/pki/rpm-gpg/PERCONA-PACKAGING-KEY
                        sudo yum -y install git svn docker sysbench
                        sudo yum -y install https://dev.mysql.com/get/mysql57-community-release-el7-11.noarch.rpm 
                        sudo yum -y install php php-mysqlnd php-pdo mysql-community-server
                        sudo amazon-linux-extras install epel -y
                        sudo yum -y install bats
                        sudo usermod -aG docker ec2-user
                        sudo systemctl start mysqld
                        sudo systemctl start docker
                        sudo mkdir -p /srv/pmm-qa || :
                        pushd /srv/pmm-qa
                            sudo git clone --single-branch --branch \${PMM_QA_GIT_BRANCH} https://github.com/percona/pmm-qa.git .
                            sudo git checkout \${PMM_QA_GIT_COMMIT_HASH}
                            sudo svn export https://github.com/Percona-QA/percona-qa.git/trunk/get_download_link.sh
                            sudo chmod 755 get_download_link.sh
                        popd
                    """
                }
                script {
                    def node = Jenkins.instance.getNode(env.VM_NAME)
                    Jenkins.instance.removeNode(node)
                    Jenkins.instance.addNode(node)                   
                }
                archiveArtifacts 'IP'
            }
        }
        stage('Run Docker') {
            when {
                expression { env.CLIENT_INSTANCE == "no" }
            }
            steps {
                script {
                    withEnv(['JENKINS_NODE_COOKIE=dontKillMe']) {
                        sh """
                        export IP=\$(cat IP)
                        export VM_NAME=\$(cat VM_NAME)

                        export CLIENT_VERSION=${CLIENT_VERSION}
                        if [[ \$CLIENT_VERSION = latest ]]; then
                            CLIENT_VERSION=\$(
                                curl -s https://www.percona.com/downloads/pmm/ \
                                    | egrep -o 'pmm/[0-9]{1,3}\\.[0-9]{1,3}\\.[0-9]{1,3}' \
                                    | sed -e 's/pmm\\///' \
                                    | sort -u -V \
                                    | tail -1
                            )
                        fi
                        """
                        node(env.VM_NAME){
                            installAWSv2()
                            withCredentials([aws(accessKeyVariable: 'AWS_ACCESS_KEY_ID', credentialsId: 'AMI/OVF', secretKeyVariable: 'AWS_SECRET_ACCESS_KEY')]) {
                                sh """
                                    set -o errexit
                                    set -o xtrace
                                    if [[ \$PMM_VERSION == pmm2 ]]; then
                                        aws ecr-public get-login-password --region us-east-1 | docker login -u AWS --password-stdin public.ecr.aws/e7j3v3n0
                                        docker create \
                                            -v /srv \
                                            --name \${VM_NAME}-data \
                                            ${DOCKER_VERSION} /bin/true

                                        docker run -d \
                                            -p 80:80 \
                                            -p 443:443 \
                                            -p 9000:9000 \
                                            --volumes-from \${VM_NAME}-data \
                                            --name \${VM_NAME}-server \
                                            --restart always \
                                            ${DOCKER_ENV_VARIABLE} \
                                            ${DOCKER_VERSION}
                                        sleep 10
                                        docker logs \${VM_NAME}-server
                                    else
                                        docker create \
                                            -v /opt/prometheus/data \
                                            -v /opt/consul-data \
                                            -v /var/lib/mysql \
                                            -v /var/lib/grafana \
                                            --name \${VM_NAME}-data \
                                            ${DOCKER_VERSION} /bin/true

                                        docker run -d \
                                            -p 80:80 \
                                            -p 443:443 \
                                            --volumes-from \${VM_NAME}-data \
                                            --name \${VM_NAME}-server \
                                            --restart always \
                                            -e METRICS_RESOLUTION=5s \
                                            ${DOCKER_VERSION}
                                        sleep 10
                                        docker logs \${VM_NAME}-server
                                    fi
                                """
                            }
                        }
                    }
                }
            }
        }
        stage('Enable Testing Repo') {
            when {
                expression { env.ENABLE_TESTING_REPO == "yes" && env.PMM_VERSION == "pmm2" && env.CLIENT_INSTANCE == "no" }
            }
            steps {
                script {
                    withEnv(['JENKINS_NODE_COOKIE=dontKillMe']) {
                        sh """
                            export IP=\$(cat IP)
                            export VM_NAME=\$(cat VM_NAME)
                        """
                        node(env.VM_NAME){
                            sh """
                                set -o errexit
                                set -o xtrace
                                docker exec \${VM_NAME}-server sed -i'' -e 's^/release/^/testing/^' /etc/yum.repos.d/pmm2-server.repo
                                docker exec \${VM_NAME}-server percona-release enable original testing
                                docker exec \${VM_NAME}-server yum clean all
                            """
                        }
                    }
                }
            }
        }
        stage('Enable Experimental Repo') {
            when {
                expression { env.PMM_VERSION == "pmm2" && env.CLIENT_INSTANCE == "no" && env.ENABLE_EXPERIMENTAL_REPO == "yes" && env.ENABLE_TESTING_REPO == "no" }
            }
            steps {
                script {
                    withEnv(['JENKINS_NODE_COOKIE=dontKillMe']) {
                        sh """
                            export IP=\$(cat IP)
                            export VM_NAME=\$(cat VM_NAME)
                        """
                        node(env.VM_NAME){
                            sh """
                                set -o errexit
                                set -o xtrace
                                docker exec \${VM_NAME}-server sed -i'' -e 's^/release/^/experimental/^' /etc/yum.repos.d/pmm2-server.repo
                                docker exec \${VM_NAME}-server percona-release enable original experimental
                                docker exec \${VM_NAME}-server yum clean all
                            """
                        }
                    }
                }
            }
        }
        stage('Run Clients') {
            steps {
                node(env.VM_NAME){
                    setupPMMClient(SERVER_IP, CLIENT_VERSION, PMM_VERSION, ENABLE_PUSH_MODE, ENABLE_TESTING_REPO, CLIENT_INSTANCE, 'aws-staging')
                    sh """
                        set -o errexit
                        set -o xtrace
                        export PATH=\$PATH:/usr/sbin
                        [ -z "${CLIENTS}" ] && exit 0 || :
                        if [[ \$PMM_VERSION == pmm1 ]]; then
                            bash /srv/pmm-qa/pmm-tests/pmm-framework.sh \
                                --pxc-version ${PXC_VERSION} \
                                --ps-version  ${PS_VERSION} \
                                --ms-version  ${MS_VERSION} \
                                --md-version  ${MD_VERSION} \
                                --mo-version  ${MO_VERSION} \
                                --pgsql-version ${PGSQL_VERSION} \
                                --download \
                                ${CLIENTS} \
                                --sysbench-data-load \
                                --sysbench-oltp-run
                        fi

                        if [[ \$PMM_VERSION == pmm2 ]]; then

                            if [[ \$CLIENT_VERSION != dev-latest ]]; then
                                export PATH="`pwd`/pmm2-client/bin:$PATH"
                            fi
                            if [[ \$CLIENT_INSTANCE == no ]]; then
                                export SERVER_IP=\$IP;
                            fi
                            bash /srv/pmm-qa/pmm-tests/pmm-framework.sh \
                                --ms-version  ${MS_VERSION} \
                                --mo-version  ${MO_VERSION} \
                                --ps-version  ${PS_VERSION} \
                                --modb-version ${MODB_VERSION} \
                                --md-version  ${MD_VERSION} \
                                --pgsql-version ${PGSQL_VERSION} \
                                --pxc-version ${PXC_VERSION} \
                                --pdpgsql-version ${PDPGSQL_VERSION} \
                                --download \
                                ${CLIENTS} \
                                --pmm2 \
                                --dbdeployer \
                                --run-load-pmm2 \
                                --query-source=${QUERY_SOURCE} \
                                --pmm2-server-ip=\$SERVER_IP
                        fi
                    """
                }
            }
        }

    }

    post {
        always {
            script {
                def node = Jenkins.instance.getNode(env.VM_NAME)
                Jenkins.instance.removeNode(node)
            }
        }
        success {
            script {
                if ("${NOTIFY}" == "true") {
                    def PUBLIC_IP = sh(returnStdout: true, script: "cat IP").trim()
                    def OWNER_FULL = sh(returnStdout: true, script: "cat OWNER_FULL").trim()
                    def OWNER_EMAIL = sh(returnStdout: true, script: "cat OWNER_EMAIL").trim()
                    def OWNER_SLACK = slackUserIdFromEmail(botUser: true, email: "${OWNER_EMAIL}", tokenCredentialId: 'JenkinsCI-SlackBot-v2')

                    slackSend botUser: true, channel: '#pmm-ci', color: '#00FF00', message: "[${JOB_NAME}]: build finished, owner: @${OWNER_FULL}, link: https://${PUBLIC_IP}"
                    slackSend botUser: true, channel: "@${OWNER_SLACK}", color: '#00FF00', message: "[${JOB_NAME}]: build finished - https://${PUBLIC_IP}"
                }
            }
        }
        failure {
            withCredentials([[$class: 'AmazonWebServicesCredentialsBinding', accessKeyVariable: 'AWS_ACCESS_KEY_ID', credentialsId: 'pmm-staging-slave', secretKeyVariable: 'AWS_SECRET_ACCESS_KEY']]) {
                sh '''
                    set -o xtrace
                    export REQUEST_ID=\$(cat REQUEST_ID)
                    if [ -n "$REQUEST_ID" ]; then
                        aws ec2 --region us-east-2 cancel-spot-instance-requests --spot-instance-request-ids \$REQUEST_ID
                        aws ec2 --region us-east-2 terminate-instances --instance-ids \$(cat ID)
                    fi
                '''
            }
            script {
                if ("${NOTIFY}" == "true") {
                    def OWNER_FULL = sh(returnStdout: true, script: "cat OWNER_FULL").trim()
                    def OWNER_EMAIL = sh(returnStdout: true, script: "cat OWNER_EMAIL").trim()
                    def OWNER_SLACK = slackUserIdFromEmail(botUser: true, email: "${OWNER_EMAIL}", tokenCredentialId: 'JenkinsCI-SlackBot-v2')

                    slackSend botUser: true, channel: '#pmm-ci', color: '#FF0000', message: "[${JOB_NAME}]: build failed, owner: @${OWNER_FULL}"
                    slackSend botUser: true, channel: "@${OWNER_SLACK}", color: '#FF0000', message: "[${JOB_NAME}]: build failed"
                }
            }
        }
    }
}
