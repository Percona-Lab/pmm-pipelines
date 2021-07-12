void pushArtifactFile(String FILE_NAME) {
    echo "Push $FILE_NAME file to S3!"

    withCredentials([[$class: 'AmazonWebServicesCredentialsBinding', accessKeyVariable: 'AWS_ACCESS_KEY_ID', credentialsId: 'AMI/OVF', secretKeyVariable: 'AWS_SECRET_ACCESS_KEY']]) {
        sh """
            touch ${FILE_NAME}
            S3_PATH=s3://percona-jenkins-artifactory/\$JOB_NAME/\$(git -C source describe --always --dirty)
            aws s3 ls \$S3_PATH/${FILE_NAME} || :
            aws s3 cp --quiet ${FILE_NAME} \$S3_PATH/${FILE_NAME} || :
        """
    }
}

void popArtifactFile(String FILE_NAME) {
    echo "Try to get $FILE_NAME file from S3!"

    withCredentials([[$class: 'AmazonWebServicesCredentialsBinding', accessKeyVariable: 'AWS_ACCESS_KEY_ID', credentialsId: 'AMI/OVF', secretKeyVariable: 'AWS_SECRET_ACCESS_KEY']]) {
        sh """
            S3_PATH=s3://percona-jenkins-artifactory/\$JOB_NAME/\$(git -C source describe --always --dirty)
            aws s3 cp --quiet \$S3_PATH/${FILE_NAME} ${FILE_NAME} || :
        """
    }
}

TestsReport = '<testsuite name=\\"PGO\\">\n'
testsReportMap = [:]
void makeReport() {
    for ( test in testsReportMap ) {
        TestsReport = TestsReport + "<testcase name=\\\"${test.key}\\\"><${test.value}/></testcase>\n"
    }
    TestsReport = TestsReport + '</testsuite>\n'
}

void runTest(String TEST_NAME) {
    def retryCount = 0
    waitUntil {
        try {
            echo "The $TEST_NAME test was started!"
            GIT_SHORT_COMMIT = sh(script: 'git -C source rev-parse --short HEAD', , returnStdout: true).trim()
            testsReportMap[TEST_NAME] = 'failure'
            PPG_TAG = sh(script: "if [ -n \"\${PGO_POSTGRES_HA_IMAGE}\" ] ; then echo ${PGO_POSTGRES_HA_IMAGE} | awk -F':' '{print \$2}' | grep -oE '[A-Za-z0-9\\.]+-ppg[0-9]{2}' ; else echo 'main-ppg13'; fi", , returnStdout: true).trim()

            popArtifactFile("${params.GIT_BRANCH}-$GIT_SHORT_COMMIT-$TEST_NAME-$PPG_TAG")

            sh """
                if [ -f "${params.GIT_BRANCH}-$GIT_SHORT_COMMIT-$TEST_NAME-$PPG_TAG" ]; then
                    echo Skip $TEST_NAME test
                else
                    cd ./source
                    if [ -n "${PGO_OPERATOR_IMAGE}" ]; then
                        export IMAGE_OPERATOR=${PGO_OPERATOR_IMAGE}
                    else
                        export IMAGE_OPERATOR=perconalab/percona-postgresql-operator:${env.GIT_BRANCH}-postgres-operator
                    fi

                    if [ -n "${PGO_APISERVER_IMAGE}" ]; then
                        export IMAGE_APISERVER=${PGO_APISERVER_IMAGE}
                    fi

                    if [ -n "${PGO_EVENT_IMAGE}" ]; then
                        export IMAGE_PGOEVENT=${PGO_EVENT_IMAGE}
                    fi

                    if [ -n "${PGO_RMDATA_IMAGE}" ]; then
                        export IMAGE_RMDATA=${PGO_RMDATA_IMAGE}
                    fi

                    if [ -n "${PGO_SCHEDULER_IMAGE}" ]; then
                        export IMAGE_SCHEDULER=${PGO_SCHEDULER_IMAGE}
                    fi

                    if [ -n "${PGO_DEPLOYER_IMAGE}" ]; then
                        export IMAGE_DEPLOYER=${PGO_DEPLOYER_IMAGE}
                    fi

                    if [ -n "${PGO_PGBOUNCER_IMAGE}" ]; then
                        export IMAGE_PGBOUNCER=${PGO_PGBOUNCER_IMAGE}
                    fi

                    if [ -n "${PGO_POSTGRES_HA_IMAGE}" ]; then
                        export IMAGE_PG_HA=${PGO_POSTGRES_HA_IMAGE}
                    fi

                    if [ -n "${PGO_BACKREST_IMAGE}" ]; then
                        export IMAGE_BACKREST=${PGO_BACKREST_IMAGE}
                    fi

                    if [ -n "${PGO_BACKREST_REPO_IMAGE}" ]; then
                        export IMAGE_BACKREST_REPO=${PGO_BACKREST_REPO_IMAGE}
                    fi

                    if [ -n "${PGO_PGBADGER_IMAGE}" ]; then
                        export IMAGE_PGBADGER=${PGO_PGBADGER_IMAGE}
                    fi

                    source $HOME/google-cloud-sdk/path.bash.inc
                    export KUBECONFIG=$WORKSPACE/openshift/auth/kubeconfig
                    oc whoami

                    ./e2e-tests/$TEST_NAME/run
                fi
            """
            pushArtifactFile("${params.GIT_BRANCH}-$GIT_SHORT_COMMIT-$TEST_NAME-$PPG_TAG")
            testsReportMap[TEST_NAME] = 'passed'
            return true
        }
        catch (exc) {
            if (retryCount >= 2) {
                currentBuild.result = 'FAILURE'
                return true
            }
            retryCount++
            return false
        }
    }

    echo "The $TEST_NAME test was finished!"
}
void installRpms() {
    sh """
        sudo yum install -y https://repo.percona.com/yum/percona-release-latest.noarch.rpm || true
        sudo percona-release enable-only tools
        sudo yum install -y percona-xtrabackup-80 jq | true
    """
}
pipeline {
    parameters {
        string(
            defaultValue: 'latest',
            description: 'OpenShift version to use',
            name: 'OS_VERSION')
        string(
            defaultValue: 'main',
            description: 'Tag/Branch for percona/percona-postgresql-operator repository',
            name: 'GIT_BRANCH')
        string(
            defaultValue: 'https://github.com/percona/percona-postgresql-operator',
            description: 'percona-postgresql-operator repository',
            name: 'GIT_REPO')
        string(
            defaultValue: '',
            description: 'Slack user to notify on failures',
            name: 'OWNER_SLACK')
        string(
            defaultValue: '',
            description: 'Operator image: perconalab/percona-postgresql-operator:main-postgres-operator',
            name: 'PGO_OPERATOR_IMAGE')
        string(
            defaultValue: '',
            description: 'Operators API server image: perconalab/percona-postgresql-operator:main-pgo-apiserver',
            name: 'PGO_APISERVER_IMAGE')
        string(
            defaultValue: '',
            description: 'Operators event server image: perconalab/percona-postgresql-operator:main-pgo-event',
            name: 'PGO_EVENT_IMAGE')
        string(
            defaultValue: '',
            description: 'Operators rmdata image: perconalab/percona-postgresql-operator:main-pgo-rmdata',
            name: 'PGO_RMDATA_IMAGE')
        string(
            defaultValue: '',
            description: 'Operators scheduler image: perconalab/percona-postgresql-operator:main-pgo-scheduler',
            name: 'PGO_SCHEDULER_IMAGE')
        string(
            defaultValue: '',
            description: 'Operators deployer image: perconalab/percona-postgresql-operator:main-pgo-deployer',
            name: 'PGO_DEPLOYER_IMAGE')
        string(
            defaultValue: '',
            description: 'Operators pgBouncer image: perconalab/percona-postgresql-operator:main-ppg13-pgbouncer',
            name: 'PGO_PGBOUNCER_IMAGE')
        string(
            defaultValue: '',
            description: 'Operators postgres image: perconalab/percona-postgresql-operator:main-ppg13-postgres-ha',
            name: 'PGO_POSTGRES_HA_IMAGE')
        string(
            defaultValue: '',
            description: 'Operators backrest utility image: perconalab/percona-postgresql-operator:main-ppg13-pgbackrest',
            name: 'PGO_BACKREST_IMAGE')
        string(
            defaultValue: '',
            description: 'Operators backrest utility image: perconalab/percona-postgresql-operator:main-ppg13-pgbackrest-repo',
            name: 'PGO_BACKREST_REPO_IMAGE')
        string(
            defaultValue: '',
            description: 'Operators pgBadger image: perconalab/percona-postgresql-operator:main-ppg13-pgbadger',
            name: 'PGO_PGBADGER_IMAGE')
    }
    environment {
        TF_IN_AUTOMATION = 'true'
    }
    agent {
         label 'docker'
    }
    options {
        buildDiscarder(logRotator(daysToKeepStr: '-1', artifactDaysToKeepStr: '-1', numToKeepStr: '10', artifactNumToKeepStr: '10'))
        skipDefaultCheckout()
        disableConcurrentBuilds()
    }

    stages {
        stage('Prepare') {
            steps {
                sh """
                    wget https://releases.hashicorp.com/terraform/0.11.14/terraform_0.11.14_linux_amd64.zip
                    unzip terraform_0.11.14_linux_amd64.zip
                    sudo mv terraform /usr/local/bin/ && rm terraform_0.11.14_linux_amd64.zip
                """
                installRpms()
                sh '''
                    if [ ! -d $HOME/google-cloud-sdk/bin ]; then
                        rm -rf $HOME/google-cloud-sdk
                        curl https://sdk.cloud.google.com | bash
                    fi

                    source $HOME/google-cloud-sdk/path.bash.inc
                    gcloud components update kubectl
                    gcloud version

                    curl -s https://get.helm.sh/helm-v3.2.3-linux-amd64.tar.gz \
                        | sudo tar -C /usr/local/bin --strip-components 1 -zvxpf -

                    curl -s -L https://mirror.openshift.com/pub/openshift-v4/clients/ocp/$OS_VERSION/openshift-client-linux.tar.gz \
                        | sudo tar -C /usr/local/bin --wildcards -zxvpf -
                    curl -s -L https://mirror.openshift.com/pub/openshift-v4/clients/ocp/$OS_VERSION/openshift-install-linux.tar.gz \
                        | sudo tar -C /usr/local/bin  --wildcards -zxvpf -

                    sudo sh -c "curl -s -L https://github.com/mikefarah/yq/releases/download/3.3.2/yq_linux_amd64 > /usr/local/bin/yq"
                    sudo chmod +x /usr/local/bin/yq
                '''

            }
        }
        stage('Build docker image') {
            steps {
                git branch: 'master', url: 'https://github.com/Percona-Lab/jenkins-pipelines'
                withCredentials([usernamePassword(credentialsId: 'hub.docker.com', passwordVariable: 'PASS', usernameVariable: 'USER'), file(credentialsId: 'cloud-secret-file', variable: 'CLOUD_SECRET_FILE'),file(credentialsId: 'cloud-minio-secret-file', variable: 'CLOUD_MINIO_SECRET_FILE')]) {
                    sh '''
                        sudo git reset --hard
                        sudo git clean -xdf
                        sudo rm -rf source
                        ./cloud/local/checkout $GIT_REPO $GIT_BRANCH

                        cp $CLOUD_SECRET_FILE ./source/e2e-tests/conf/cloud-secret.yml
                        cp $CLOUD_MINIO_SECRET_FILE ./source/e2e-tests/conf/cloud-secret-minio-gw.yml

                        if [ -n "${PGO_OPERATOR_IMAGE}" ]; then
                            echo "SKIP: Build is not needed, PG operator image was set!"
                        else
                            cd ./source/
                            sg docker -c "
                                docker login -u '${USER}' -p '${PASS}'
                                export IMAGE_URI_BASE=perconalab/percona-postgresql-operator:$GIT_BRANCH
                                ./e2e-tests/build
                                docker logout
                            "
                            sudo rm -rf ./build
                        fi
                    '''
                }
            }
        }
        stage('Create AWS Infrastructure') {
            steps {
                withCredentials([[$class: 'AmazonWebServicesCredentialsBinding', accessKeyVariable: 'AWS_ACCESS_KEY_ID', credentialsId: 'openshift-cicd'], file(credentialsId: 'aws-openshift-41-key-pub', variable: 'AWS_NODES_KEY_PUB'), file(credentialsId: 'pgo-openshift4-secret-file', variable: 'OPENSHIFT_CONF_FILE')]) {
                     sh """
                         mkdir openshift
                         cp $OPENSHIFT_CONF_FILE ./openshift/install-config.yaml
                     """
                     sshagent(['aws-openshift-41-key']) {
                         sh """
                             /usr/local/bin/openshift-install create cluster --dir=./openshift/
                         """
                    }
               }

            }
        }
        stage('Run Tests') {
            environment {
                CLEAN_NAMESPACE = 1
            }
            steps {
                runTest('init-deploy')
                runTest('scaling')
                runTest('recreate')
                runTest('affinity')
                runTest('demand-backup')
            }
        }
        stage('Make report') {
            steps {
                makeReport()
                sh """
                    echo "${TestsReport}" > TestsReport.xml
                """
                step([$class: 'JUnitResultArchiver', testResults: '*.xml', healthScaleFactor: 1.0])
                archiveArtifacts '*.xml'
            }
        }
    }

    post {
        always {
                if (currentBuild.result != null && currentBuild.result != 'SUCCESS') {
                    slackSend channel: '#cloud-dev-ci', color: '#FF0000', message: "[${JOB_NAME}]: build ${currentBuild.result}, ${BUILD_URL}"
                    slackSend channel: '@${OWNER_SLACK}', color: '#FF0000', message: "[${JOB_NAME}]: build ${currentBuild.result}, ${BUILD_URL}"
                }
                withCredentials([[$class: 'AmazonWebServicesCredentialsBinding', accessKeyVariable: 'AWS_ACCESS_KEY_ID', credentialsId: 'openshift-cicd'], file(credentialsId: 'aws-openshift-41-key-pub', variable: 'AWS_NODES_KEY_PUB'), file(credentialsId: 'pgo-openshift4-secret-file', variable: 'OPENSHIFT-CONF-FILE')]) {
                     sshagent(['aws-openshift-41-key']) {
                         sh """
                             /usr/local/bin/openshift-install destroy cluster --dir=./openshift/
                         """
                     }
                }

            sh '''
                sudo docker system prune -fa
                sudo rm -rf $HOME/google-cloud-sdk
                sudo rm -rf ./*
            '''
            deleteDir()
        }
    }
}
