library changelog: false, identifier: 'lib@master', retriever: modernSCM([
    $class: 'GitSCMSource',
    remote: 'https://github.com/Percona-Lab/jenkins-pipelines.git'
]) _

void runStagingServer(String DOCKER_VERSION, CLIENT_VERSION, CLIENTS, CLIENT_INSTANCE, SERVER_IP) {
    stagingJob = build job: 'aws-staging-start', parameters: [
        string(name: 'DOCKER_VERSION', value: DOCKER_VERSION),
        string(name: 'CLIENT_VERSION', value: CLIENT_VERSION),
        string(name: 'CLIENTS', value: CLIENTS),
        string(name: 'CLIENT_INSTANCE', value: CLIENT_INSTANCE),
        string(name: 'PS_VERSION', value: '5.7.30'),
        string(name: 'QUERY_SOURCE', value: 'slowlog'),
        string(name: 'DOCKER_ENV_VARIABLE', value: '-e ENABLE_DBAAS=1 -e PMM_DEBUG=1 -e PERCONA_TEST_SAAS_HOST=check-dev.percona.com:443 -e PERCONA_TEST_CHECKS_PUBLIC_KEY=RWTg+ZmCCjt7O8eWeAmTLAqW+1ozUbpRSKSwNTmO+exlS5KEIPYWuYdX -e PERCONA_TEST_CHECKS_INTERVAL=10s'),
        string(name: 'SERVER_IP', value: SERVER_IP),
        string(name: 'NOTIFY', value: 'false'),
        string(name: 'DAYS', value: '1')
    ]
    env.VM_IP = stagingJob.buildVariables.IP
    env.SERVER_IP = env.VM_IP
    env.VM_NAME = stagingJob.buildVariables.VM_NAME
    def clientInstance = "yes";
    if ( CLIENT_INSTANCE == clientInstance ) {
        env.PMM_URL = "http://admin:admin@${SERVER_IP}"
        env.PMM_UI_URL = "http://${SERVER_IP}/"
    }
    else
    {
        env.PMM_URL = "http://admin:admin@${VM_IP}"
        env.PMM_UI_URL = "http://${VM_IP}/"
    }
}

void runClusterStaging(String PMM_QA_GIT_BRANCH) {
    clusterJob = build job: 'kubernetes-cluster-staging', parameters: [
        string(name: 'NOTIFY', value: 'false'),
        string(name: 'PMM_QA_GIT_BRANCH', value: PMM_QA_GIT_BRANCH),
        string(name: 'DAYS', value: '1')
    ]
    env.CLUSTER_IP = clusterJob.buildVariables.IP
    env.KUBECONFIG = clusterJob.buildVariables.KUBECONFIG
}

void destroyStaging(IP) {
    build job: 'aws-staging-stop', parameters: [
        string(name: 'VM', value: IP),
    ]
}

void uploadAllureArtifacts() {
    withCredentials([sshUserPrivateKey(credentialsId: 'aws-jenkins', keyFileVariable: 'KEY_PATH', passphraseVariable: '', usernameVariable: 'USER')]) {
        sh """
            scp -r -i "${KEY_PATH}" -o ConnectTimeout=1 -o StrictHostKeyChecking=no \
                tests/output/allure aws-jenkins@${MONITORING_HOST}:/home/aws-jenkins/allure-reports
        """
    }
}
pipeline {
    agent {
        label 'large-amazon'
    }
    environment {
        AZURE_CLIENT_ID=credentials('AZURE_CLIENT_ID');
        AZURE_CLIENT_SECRET=credentials('AZURE_CLIENT_SECRET');
        AZURE_MYSQL_HOST=credentials('AZURE_MYSQL_HOST');
        AZURE_MYSQL_PASS=credentials('AZURE_MYSQL_PASS');
        AZURE_MYSQL_USER=credentials('AZURE_MYSQL_USER');
        AZURE_POSTGRES_HOST=credentials('AZURE_POSTGRES_HOST');
        AZURE_POSTGRES_PASS=credentials('AZURE_POSTGRES_PASS');
        AZURE_POSTGRES_USER=credentials('AZURE_POSTGRES_USER');
        AZURE_SUBSCRIPTION_ID=credentials('AZURE_SUBSCRIPTION_ID');
        AZURE_TENNANT_ID=credentials('AZURE_TENNANT_ID');
        REMOTE_AWS_MYSQL_USER=credentials('pmm-dev-mysql-remote-user')
        REMOTE_AWS_MYSQL_PASSWORD=credentials('pmm-dev-remote-password')
        REMOTE_AWS_MYSQL57_HOST=credentials('pmm-dev-mysql57-remote-host')
        REMOTE_AWS_POSTGRES12_USER=credentials('pmm-qa-postgres-12-user')
        REMOTE_AWS_POSTGRES12_PASSWORD=credentials('pmm-qa-postgres-12-password')
        REMOTE_MYSQL_HOST=credentials('mysql-remote-host')
        REMOTE_MYSQL_USER=credentials('mysql-remote-user')
        REMOTE_MYSQL_PASSWORD=credentials('mysql-remote-password')
        REMOTE_MONGODB_HOST=credentials('qa-remote-mongodb-host')
        REMOTE_MONGODB_USER=credentials('qa-remote-mongodb-user')
        REMOTE_MONGODB_PASSWORD=credentials('qa-remote-mongodb-password')
        REMOTE_POSTGRESQL_HOST=credentials('qa-remote-pgsql-host')
        REMOTE_POSTGRESQL_USER=credentials('qa-remote-pgsql-user')
        REMOTE_POSTGRESSQL_PASSWORD=credentials('qa-remote-pgsql-password')
        REMOTE_PROXYSQL_HOST=credentials('qa-remote-proxysql-host')
        REMOTE_PROXYSQL_USER=credentials('qa-remote-proxysql-user')
        REMOTE_PROXYSQL_PASSWORD=credentials('qa-remote-proxysql-password')
        INFLUXDB_ADMIN_USER=credentials('influxdb-admin-user')
        INFLUXDB_ADMIN_PASSWORD=credentials('influxdb-admin-password')
        INFLUXDB_USER=credentials('influxdb-user')
        INFLUXDB_USER_PASSWORD=credentials('influxdb-user-password')
        MONITORING_HOST=credentials('monitoring-host')
        EXTERNAL_EXPORTER_PORT=credentials('external-exporter-port')
        MAILOSAUR_API_KEY=credentials('MAILOSAUR_API_KEY')
        MAILOSAUR_SERVER_ID=credentials('MAILOSAUR_SERVER_ID')
        MAILOSAUR_SMTP_PASSWORD=credentials('MAILOSAUR_SMTP_PASSWORD')
    }
    parameters {
        string(
            defaultValue: 'main',
            description: 'Tag/Branch for pmm-ui-tests repository',
            name: 'GIT_BRANCH')
        string(
            defaultValue: '',
            description: 'Commit hash for the branch',
            name: 'GIT_COMMIT_HASH')
        string(
            defaultValue: 'perconalab/pmm-server:dev-latest',
            description: 'PMM Server docker container version (image-name:version-tag)',
            name: 'DOCKER_VERSION')
        string(
            defaultValue: 'dev-latest',
            description: 'PMM Client version',
            name: 'CLIENT_VERSION')
        choice(
            choices: ['no', 'yes'],
            description: "Use this instance only as a client host",
            name: 'CLIENT_INSTANCE')
        choice(
            choices: ['no', 'yes'],
            description: "Run AMI Setup Wizard for AMI UI tests",
            name: 'AMI_TEST')
        choice(
            choices: ['no', 'yes'],
            description: "Run Tests for OVF supported Features",
            name: 'OVF_TEST')
        string(
            defaultValue: '',
            description: 'AMI Instance ID',
            name: 'AMI_INSTANCE_ID')
        string (
            defaultValue: '',
            description: 'Value for Server Public IP, to use this instance just as client',
            name: 'SERVER_IP')
        string(
            defaultValue: 'master',
            description: 'Tag/Branch for pmm-qa repository',
            name: 'PMM_QA_GIT_BRANCH')
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
                // clean up workspace and fetch pmm-ui-tests repository
                deleteDir()
                git poll: false, branch: GIT_BRANCH, url: 'https://github.com/percona/pmm-ui-tests.git'

                installDocker()
                sh '''
                    sudo yum -y update --security
                    sudo yum -y install jq svn
                    sudo usermod -aG docker ec2-user
                    sudo service docker start
                    sudo mkdir -p /srv/pmm-qa || :
                    pushd /srv/pmm-qa
                        sudo git clone --single-branch --branch \${PMM_QA_GIT_BRANCH} https://github.com/percona/pmm-qa.git .
                        sudo git checkout \${PMM_QA_GIT_COMMIT_HASH}
                        sudo chmod 755 pmm-tests/install-google-chrome.sh
                        bash ./pmm-tests/install-google-chrome.sh
                    popd
                    sudo ln -s /usr/bin/google-chrome-stable /usr/bin/chromium
                '''
            }
        }
        stage('Checkout Commit') {
            when {
                expression { env.GIT_COMMIT_HASH.length()>0 }
            }
            steps {
                sh 'git checkout ' + env.GIT_COMMIT_HASH
            }
        }
        stage('Setup PMM Server and Kubernetes Cluster') {
            parallel {
                stage('Start PMM Cluster Staging Instance') {
                    when {
                        expression { env.AMI_TEST == "no" && env.OVF_TEST == "no" }
                    }
                    steps {
                        runClusterStaging('master')
                    }
                }
                stage('Start PMM Server Instance') {
                    when {
                        expression { env.CLIENT_INSTANCE == "no" }
                    }
                    steps {
                        runStagingServer(DOCKER_VERSION, CLIENT_VERSION, '--addclient=haproxy,1 --addclient=ps,1', CLIENT_INSTANCE, SERVER_IP)
                    }
                }
            }
        }
        stage('Setup PMM Server Information') {
            when {
                expression { env.CLIENT_INSTANCE == "yes" }
            }
            steps {
                script {
                    env.PMM_URL = "http://admin:admin@${SERVER_IP}"
                    env.PMM_UI_URL = "http://${SERVER_IP}/"
                }
            }
        }
        stage('Setup') {
            parallel {
                stage('Sanity check') {
                    steps {
                        sh 'timeout 100 bash -c \'while [[ "$(curl -s -o /dev/null -w \'\'%{http_code}\'\' \${PMM_URL}/ping)" != "200" ]]; do sleep 5; done\' || false'
                    }
                }
                stage('Setup Node') {
                    steps {
                        sh """
                            curl --silent --location https://rpm.nodesource.com/setup_14.x | sudo bash -
                            sudo yum -y install nodejs

                            npm install
                            node -v
                            npm -v
                            sudo yum install -y gettext
                            envsubst < env.list > env.generated.list
                        """
                    }
                }
            }
        }
        stage('Run UI Tests Docker') {
            options {
                timeout(time: 35, unit: "MINUTES")
            }
            when {
                expression { env.AMI_TEST == "no" && env.OVF_TEST == "no" }
            }
            steps {
                withCredentials([[$class: 'AmazonWebServicesCredentialsBinding', accessKeyVariable: 'AWS_ACCESS_KEY_ID', credentialsId: 'PMM_AWS_DEV', secretKeyVariable: 'AWS_SECRET_ACCESS_KEY']]) {
                    sh """
                        sed -i 's+http://localhost/+${PMM_UI_URL}/+g' pr.codecept.js
                        export PWD=\$(pwd);
                        export CHROMIUM_PATH=/usr/bin/chromium
                        export kubeconfig_minikube="${KUBECONFIG}"
                        ./node_modules/.bin/codeceptjs run-multiple parallel --debug --steps --reporter mocha-multi -c pr.codecept.js --grep '(?=.*)^(?!.*@pmm-upgrade)^(?!.*@qan)^(?!.*@nightly)'
                    """
                }
            }
        }
        stage('Run UI Tests OVF') {
            options {
                timeout(time: 35, unit: "MINUTES")
            }
            when {
                expression { env.OVF_TEST == "yes" }
            }
            steps {
                withCredentials([[$class: 'AmazonWebServicesCredentialsBinding', accessKeyVariable: 'AWS_ACCESS_KEY_ID', credentialsId: 'PMM_AWS_DEV', secretKeyVariable: 'AWS_SECRET_ACCESS_KEY']]) {
                    sh """
                        sed -i 's+http://localhost/+${PMM_UI_URL}/+g' pr.codecept.js
                        export PWD=\$(pwd);
                        export CHROMIUM_PATH=/usr/bin/chromium
                        ./node_modules/.bin/codeceptjs run --debug --steps --reporter mocha-multi -c pr.codecept.js --grep '(?=.*)^(?!.*@pmm-upgrade)^(?!.*@qan)^(?!.*@dbaas)'
                    """
                }
            }
        }
    }
    post {
        always {
            // stop staging
            sh '''
                curl --insecure ${PMM_URL}/logs.zip --output logs.zip || true
                ./node_modules/.bin/mochawesome-merge tests/output/parallel_chunk*/*.json > tests/output/combine_results.json || true
                ./node_modules/.bin/marge tests/output/combine_results.json --reportDir tests/output/ --inline --cdn --charts || true
            '''
            script {
                if(env.VM_NAME)
                {
                    destroyStaging(VM_NAME)
                }
                if(env.VM_CLIENT_NAME)
                {
                    destroyStaging(VM_CLIENT_NAME)
                }
                if(env.CLUSTER_IP)
                {
                    destroyStaging(CLUSTER_IP)
                }
            }
            script {
                if (currentBuild.result == null || currentBuild.result == 'SUCCESS') {
                    junit 'tests/output/parallel_chunk*/*.xml'
                    publishHTML([allowMissing: false, alwaysLinkToLastBuild: false, keepAll: false, reportDir: 'tests/output/', reportFiles: 'combine_results.html', reportName: 'HTML Report', reportTitles: ''])
                    archiveArtifacts artifacts: 'tests/output/combine_results.html'
                    archiveArtifacts artifacts: 'logs.zip'
                } else {
                    junit 'tests/output/parallel_chunk*/*.xml'
                    publishHTML([allowMissing: false, alwaysLinkToLastBuild: false, keepAll: false, reportDir: 'tests/output/', reportFiles: 'combine_results.html', reportName: 'HTML Report', reportTitles: ''])
                    slackSend botUser: true, channel: '#pmm-ci', color: '#FF0000', message: "[${JOB_NAME}]: build ${currentBuild.result} - ${BUILD_URL}"
                    archiveArtifacts artifacts: 'tests/output/combine_results.html'
                    archiveArtifacts artifacts: 'logs.zip'
                    archiveArtifacts artifacts: 'tests/output/parallel_chunk*/*.png'
                }
            }
            allure([
                includeProperties: false,
                jdk: '',
                properties: [],
                reportBuildPolicy: 'ALWAYS',
                results: [[path: 'tests/output/allure']]
            ])
            sh '''
                sudo rm -r node_modules/
                sudo rm -r tests/output
            '''
            deleteDir()
        }
    }
}
