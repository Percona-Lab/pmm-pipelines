import hudson.model.Node.Mode
import hudson.slaves.*
import jenkins.model.Jenkins
import hudson.plugins.sshslaves.SSHLauncher

void runStaging(String DOCKER_VERSION, CLIENT_VERSION, CLIENTS, PMM_QA_GIT_BRANCH, PMM_QA_GIT_COMMIT_HASH) {
    stagingJob = build job: 'aws-staging-start', parameters: [
        string(name: 'DOCKER_VERSION', value: DOCKER_VERSION),
        string(name: 'CLIENT_VERSION', value: CLIENT_VERSION),
        string(name: 'CLIENTS', value: ''),
        string(name: 'NOTIFY', value: 'false'),
        string(name: 'DAYS', value: '1'),
        string(name: 'PMM_QA_GIT_BRANCH', value: PMM_QA_GIT_BRANCH),
        string(name: 'PMM_QA_GIT_COMMIT_HASH', value: PMM_QA_GIT_COMMIT_HASH)
    ]
    env.VM_IP = stagingJob.buildVariables.IP
    env.VM_NAME = stagingJob.buildVariables.VM_NAME
    env.PMM_URL = "http://admin:admin@${VM_IP}"
}

void destroyStaging(IP) {
    build job: 'aws-staging-stop', parameters: [
        string(name: 'VM', value: IP),
    ]
}

void runTAP(String TYPE, String PRODUCT, String COUNT, String VERSION) {
    node(env.VM_NAME){
        installAWSv2()
        withCredentials([aws(accessKeyVariable: 'AWS_ACCESS_KEY_ID', credentialsId: 'AMI/OVF', secretKeyVariable: 'AWS_SECRET_ACCESS_KEY')]) {
            sh """
                set -o errexit
                set -o xtrace

                aws ecr-public get-login-password --region us-east-1 | docker login -u AWS --password-stdin public.ecr.aws/e7j3v3n0

                test -f /usr/lib64/libsasl2.so.2 || sudo ln -s /usr/lib64/libsasl2.so.3.0.0 /usr/lib64/libsasl2.so.2
                export PATH=\$PATH:/usr/sbin
                export instance_t="${TYPE}"
                export instance_c="${COUNT}"
                export version="${VERSION}"
                export pmm_server_ip="${VM_IP}"
                export stress="1"
                export table_c="100"
                export tap="1"
                export PMM_VERSION=${PMM_VERSION}

                sudo chmod 755 /srv/pmm-qa/pmm-tests/pmm-framework.sh
                export CLIENT_VERSION=${CLIENT_VERSION}
                if [[ \$CLIENT_VERSION == http* ]]; then
                    export PATH="/home/ec2-user/workspace/aws-staging-start/pmm2-client/bin:$PATH"
                fi
                bash /srv/pmm-qa/pmm-tests/pmm-2-0-bats-tests/pmm-testsuite.sh \
                    | tee /tmp/result.output

                mv /tmp/result.output /tmp/result.tap
            """
        }
    }
    withCredentials([sshUserPrivateKey(credentialsId: 'aws-jenkins', keyFileVariable: 'KEY_PATH', passphraseVariable: '', usernameVariable: 'USER')]) {
        sh """
            scp -i "${KEY_PATH}" -o ConnectTimeout=1 -o StrictHostKeyChecking=no \
                ${USER}@${VM_IP}:/tmp/result.tap \
                ${TYPE}_${VERSION}.tap
            cat ${TYPE}_${VERSION}.tap \
                | ./node_modules/tap-junit/bin/tap-junit \
                    --name ${TYPE}_${VERSION}.xml \
                    --output ./ \
                    --pretty \
                    || :
        """
    }
}

void fetchAgentLog(String CLIENT_VERSION) {
     withCredentials([sshUserPrivateKey(credentialsId: 'aws-jenkins', keyFileVariable: 'KEY_PATH', passphraseVariable: '', usernameVariable: 'USER')]) {
        sh """
            ssh -i "${KEY_PATH}" -o ConnectTimeout=1 -o StrictHostKeyChecking=no ${USER}@${VM_IP} '
                set -o errexit
                set -o xtrace
                export CLIENT_VERSION=${CLIENT_VERSION}
                if [[ \$CLIENT_VERSION != http* ]]; then
                    journalctl -u pmm-agent.service > /var/log/pmm-agent.log
                    sudo chmod 777 /var/log/pmm-agent.log
                fi
                if [[ -e /var/log/pmm-agent.log ]]; then
                    cp /var/log/pmm-agent.log .
                fi
            '
            if [[ \$CLIENT_VERSION != http* ]]; then
                scp -i "${KEY_PATH}" -o ConnectTimeout=1 -o StrictHostKeyChecking=no \
                    ${USER}@${VM_IP}:pmm-agent.log \
                    pmm-agent.log
            fi
        """
    }
    withCredentials([sshUserPrivateKey(credentialsId: 'aws-jenkins', keyFileVariable: 'KEY_PATH', passphraseVariable: '', usernameVariable: 'USER')]) {
        sh """
            if [[ \$CLIENT_VERSION == http* ]]; then
                scp -i "${KEY_PATH}" -o ConnectTimeout=1 -o StrictHostKeyChecking=no \
                    ${USER}@${VM_IP}:workspace/aws-staging-start/pmm-agent.log \
                    pmm-agent.log
            fi
        """
    }
}

pipeline {
    agent {
        label 'micro-amazon'
    }
    parameters {
        string(
            defaultValue: 'public.ecr.aws/e7j3v3n0/pmm-server:dev-latest',
            description: 'PMM Server docker container version (image-name:version-tag)',
            name: 'DOCKER_VERSION')
        string(
            defaultValue: 'dev-latest',
            description: 'PMM Client version',
            name: 'CLIENT_VERSION')
        string(
            defaultValue: 'master',
            description: 'Tag/Branch for pmm-qa repository',
            name: 'PMM_QA_GIT_BRANCH')
        string(
            defaultValue: '',
            description: 'Commit hash for pmm-qa branch',
            name: 'PMM_QA_GIT_COMMIT_HASH')
        string(
            defaultValue: '2.20.0',
            description: 'pmm2-client latest version',
            name: 'PMM_VERSION')
    }
    options {
        skipDefaultCheckout()
    }
    triggers {
        upstream upstreamProjects: 'pmm2-server-autobuild', threshold: hudson.model.Result.SUCCESS
    }

    stages {
        stage('Prepare') {
            steps {
                deleteDir()
                slackSend channel: '#pmm-ci', color: '#FFFF00', message: "[${JOB_NAME}]: build started - ${BUILD_URL}"
                sh '''
                    curl --silent --location https://rpm.nodesource.com/setup_14.x | sudo bash -
                    sudo yum -y install nodejs
                    npm install tap-junit
                '''
            }
        }
        stage('Start staging') {
            steps {
                runStaging(DOCKER_VERSION, CLIENT_VERSION, '--addclient=ps,1 --pmm2', PMM_QA_GIT_BRANCH, PMM_QA_GIT_COMMIT_HASH)
                script {

                    SSHLauncher ssh_connection = new SSHLauncher(env.VM_IP, 22, 'aws-jenkins')
                    DumbSlave node = new DumbSlave(env.VM_NAME, "spot instance job", "/home/ec2-user/", "1", Mode.EXCLUSIVE, "", ssh_connection, RetentionStrategy.INSTANCE)

                    Jenkins.instance.addNode(node)
                }
            }
        }
        stage('Sanity check') {
            steps {
                sh 'timeout 100 bash -c \'while [[ "$(curl -s -o /dev/null -w \'\'%{http_code}\'\' \${PMM_URL}/ping)" != "200" ]]; do sleep 5; done\' || false'
            }
        }
        stage('Test: PS57') {
            steps {
                runTAP("ps", "ps", "2", "5.7")
            }
        }
        stage('Test: PSMDB_4_0') {
            steps {
                runTAP("mo", "psmdb", "3", "4.0")
            }
        }
        stage('Test: PS80') {
            steps {
                runTAP("ps", "ps", "2", "8.0")
            }
        }
        stage('Test: PSMDB_3_6') {
            steps {
                runTAP("mo", "psmdb", "3", "3.6")
            }
        }
        stage('Test: MDB_4_2') {
            steps {
                runTAP("modb", "modb", "3", "4.2")
            }
        }
        stage('Test: MDB_4_0') {
            steps {
                runTAP("modb", "modb", "3", "4.0")
            }
        }
        stage('Test: HAPROXY') {
            steps {
                runTAP("haproxy", "haproxy", "1", "2.4")
            }
        }
        stage('Test: MS57') {
            steps {
                runTAP("ms", "mysql", "2", "5.7")
            }
        }
        stage('Test: MS80') {
            steps {
                runTAP("ms", "mysql", "2", "8.0")
            }
        }
        stage('Test: PGSQL10') {
            steps {
                runTAP("pgsql", "postgresql", "3", "10.6")
            }
        }
        stage('Test: PD_PGSQL12') {
            steps {
                runTAP("pdpgsql", "postgresql", "1", "12")
            }
        }
        stage('Test: PXC') {
            steps {
                runTAP("pxc", "pxc", "1", "5.7")
            }
        }
        stage('Check Results') {
            steps {
                script {
                    OK = sh (
                        script: 'grep "^ok" *.tap | grep -v "# skip" | wc -l',
                        returnStdout: true
                    ).trim()
                    SKIP = sh (
                        script: 'grep "# skip" *.tap | wc -l',
                        returnStdout: true
                    ).trim()
                    FAIL = sh (
                        script: 'grep "^not ok" *.tap | wc -l',
                        returnStdout: true
                    ).trim()
                    if (FAIL.toInteger() > 0) {
                        sh "exit 1"
                    }
                }
            }
        }
    }
    post {
        always {
            sh '''
                curl --insecure ${PMM_URL}/logs.zip --output logs.zip || true
            '''
            fetchAgentLog(CLIENT_VERSION)
            script {
                junit allowEmptyResults: true, testResults: '**/*.xml'
            }
            script {
                if(env.VM_NAME) {
                    destroyStaging(VM_NAME)
                    archiveArtifacts artifacts: 'logs.zip'
                    archiveArtifacts artifacts: 'pmm-agent.log'
                }
                def node = Jenkins.instance.getNode(env.VM_NAME)
                Jenkins.instance.removeNode(node)
            }
        }
        unstable {
            slackSend channel: '#pmm-ci', color: '#00FF00', message: "[${JOB_NAME}]: build failed\nok - ${OK}, skip - ${SKIP}, fail - ${FAIL}\ncheck here: ${BUILD_URL}"
        }
        success {
            slackSend channel: '#pmm-ci', color: '#00FF00', message: "[${JOB_NAME}]: build Passed\nok - ${OK}, skip - ${SKIP}, fail - ${FAIL}\ncheck here: ${BUILD_URL}"
        }
        failure {
            slackSend channel: '#pmm-ci', color: '#FF0000', message: "[${JOB_NAME}]: build failed\nok - ${OK}, skip - ${SKIP}, fail - ${FAIL}\ncheck here: ${BUILD_URL}"
        }
    }
}
