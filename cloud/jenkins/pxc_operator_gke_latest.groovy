void CreateCluster(String CLUSTER_SUFFIX) {
    withCredentials([string(credentialsId: 'GCP_PROJECT_ID', variable: 'GCP_PROJECT'), file(credentialsId: 'gcloud-alpha-key-file', variable: 'CLIENT_SECRET_FILE')]) {
        sh """
            export KUBECONFIG=/tmp/$CLUSTER_NAME-${CLUSTER_SUFFIX}
            source $HOME/google-cloud-sdk/path.bash.inc
            gcloud auth activate-service-account alpha-svc-acct@"${GCP_PROJECT}".iam.gserviceaccount.com --key-file=$CLIENT_SECRET_FILE
            gcloud config set project $GCP_PROJECT
            gcloud alpha container clusters create --release-channel rapid $CLUSTER_NAME-${CLUSTER_SUFFIX} --cluster-version ${params.GKE_VERSION} --zone us-central1-a --project $GCP_PROJECT --preemptible --machine-type n1-standard-4 --num-nodes=4 --enable-autoscaling --min-nodes=4 --max-nodes=6 --network=jenkins-vpc --subnetwork=jenkins-${CLUSTER_SUFFIX}
            kubectl create clusterrolebinding cluster-admin-binding1 --clusterrole=cluster-admin --user=\$(gcloud config get-value core/account)
        """
   }
}
void ShutdownCluster(String CLUSTER_SUFFIX) {
    withCredentials([string(credentialsId: 'GCP_PROJECT_ID', variable: 'GCP_PROJECT'), file(credentialsId: 'gcloud-alpha-key-file', variable: 'CLIENT_SECRET_FILE')]) {
        sh """
            export KUBECONFIG=/tmp/$CLUSTER_NAME-${CLUSTER_SUFFIX}
            source $HOME/google-cloud-sdk/path.bash.inc
            gcloud auth activate-service-account alpha-svc-acct@"${GCP_PROJECT}".iam.gserviceaccount.com --key-file=$CLIENT_SECRET_FILE
            gcloud config set project $GCP_PROJECT
            gcloud container clusters delete --zone us-central1-a $CLUSTER_NAME-${CLUSTER_SUFFIX}
        """
   }
}
void pushArtifactFile(String FILE_NAME) {
    echo "Push $FILE_NAME file to S3!"

    withCredentials([[$class: 'AmazonWebServicesCredentialsBinding', accessKeyVariable: 'AWS_ACCESS_KEY_ID', credentialsId: 'AMI/OVF', secretKeyVariable: 'AWS_SECRET_ACCESS_KEY']]) {
        sh """
            touch ${FILE_NAME}
            S3_PATH=s3://percona-jenkins-artifactory/\$JOB_NAME/${env.GIT_SHORT_COMMIT}
            aws s3 ls \$S3_PATH/${FILE_NAME} || :
            aws s3 cp --quiet ${FILE_NAME} \$S3_PATH/${FILE_NAME} || :
        """
    }
}

void popArtifactFile(String FILE_NAME) {
    echo "Try to get $FILE_NAME file from S3!"

    withCredentials([[$class: 'AmazonWebServicesCredentialsBinding', accessKeyVariable: 'AWS_ACCESS_KEY_ID', credentialsId: 'AMI/OVF', secretKeyVariable: 'AWS_SECRET_ACCESS_KEY']]) {
        sh """
            S3_PATH=s3://percona-jenkins-artifactory/\$JOB_NAME/${env.GIT_SHORT_COMMIT}
            aws s3 cp --quiet \$S3_PATH/${FILE_NAME} ${FILE_NAME} || :
        """
    }
}

TestsReport = '<testsuite name=\\"PXC\\">\n'
testsReportMap = [:]
void makeReport() {
    for ( test in testsReportMap ) {
        TestsReport = TestsReport + "<testcase name=\\\"${test.key}\\\"><${test.value}/></testcase>\n"
    }
    TestsReport = TestsReport + '</testsuite>\n'
}

void runTest(String TEST_NAME, String CLUSTER_SUFFIX) {
    def retryCount = 0
    waitUntil {
        try {
            echo "The $TEST_NAME test was started!"
            PXC_TAG = sh(script: "if [ -n \"\${IMAGE_PXC}\" ] ; then echo ${IMAGE_PXC} | awk -F':' '{print \$2}'; else echo 'main'; fi", , returnStdout: true).trim()
            testsReportMap[TEST_NAME] = 'failure'

            popArtifactFile("${params.GIT_BRANCH}-${env.GIT_SHORT_COMMIT}-$TEST_NAME-${params.GKE_VERSION}-$PXC_TAG-CW_${params.CLUSTER_WIDE}")

            timeout(time: 90, unit: 'MINUTES') {
                sh """
                    if [ -f "${params.GIT_BRANCH}-${env.GIT_SHORT_COMMIT}-$TEST_NAME-${params.GKE_VERSION}-$PXC_TAG-CW_${params.CLUSTER_WIDE}" ]; then
                        echo Skip $TEST_NAME test
                    else
                        cd ./source
                        if [ -n "${PXC_OPERATOR_IMAGE}" ]; then
                            export IMAGE=${PXC_OPERATOR_IMAGE}
                        else
                            export IMAGE=perconalab/percona-xtradb-cluster-operator:${env.GIT_BRANCH}
                        fi

                        if [ -n "${IMAGE_PXC}" ]; then
                            export IMAGE_PXC=${IMAGE_PXC}
                        fi

                        if [ -n "${IMAGE_PROXY}" ]; then
                            export IMAGE_PROXY=${IMAGE_PROXY}
                        fi

                        if [ -n "${IMAGE_HAPROXY}" ]; then
                            export IMAGE_HAPROXY=${IMAGE_HAPROXY}
                        fi

                        if [ -n "${IMAGE_BACKUP}" ]; then
                            export IMAGE_BACKUP=${IMAGE_BACKUP}
                        fi

                        if [ -n "${IMAGE_PMM}" ]; then
                            export IMAGE_PMM=${IMAGE_PMM}
                        fi

                        if [ -n "${IMAGE_LOGCOLLECTOR}" ]; then
                            export IMAGE_LOGCOLLECTOR=${IMAGE_LOGCOLLECTOR}
                        fi

                        export KUBECONFIG=/tmp/$CLUSTER_NAME-${CLUSTER_SUFFIX}
                        source $HOME/google-cloud-sdk/path.bash.inc
                        ./e2e-tests/$TEST_NAME/run
                    fi
                """
            }
            pushArtifactFile("${params.GIT_BRANCH}-${env.GIT_SHORT_COMMIT}-$TEST_NAME-${params.GKE_VERSION}-$PXC_TAG-CW_${params.CLUSTER_WIDE}")
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
    sh '''
        sudo yum install -y https://repo.percona.com/yum/percona-release-latest.noarch.rpm || true
        sudo percona-release enable-only tools
        sudo yum install -y percona-xtrabackup-80 jq | true
    '''
}
pipeline {
    environment {
        CLOUDSDK_CORE_DISABLE_PROMPTS = 1
    }
    parameters {
        string(
            defaultValue: 'main',
            description: 'Tag/Branch for percona/percona-xtradb-cluster-operator repository',
            name: 'GIT_BRANCH')
        string(
            defaultValue: 'https://github.com/percona/percona-xtradb-cluster-operator',
            description: 'percona-xtradb-cluster-operator repository',
            name: 'GIT_REPO')
        string(
            defaultValue: 'latest',
            description: 'GKE version',
            name: 'GKE_VERSION')
        string(
            defaultValue: '',
            description: 'Slack user to notify on failures',
            name: 'OWNER_SLACK')
        choice(
            choices: 'NO\nYES',
            description: 'Run tests with cluster wide',
            name: 'CLUSTER_WIDE')
        string(
            defaultValue: '',
            description: 'Operator image: perconalab/percona-xtradb-cluster-operator:main',
            name: 'PXC_OPERATOR_IMAGE')
        string(
            defaultValue: '',
            description: 'PXC image: perconalab/percona-xtradb-cluster-operator:main-pxc8.0',
            name: 'IMAGE_PXC')
        string(
            defaultValue: '',
            description: 'PXC proxy image: perconalab/percona-xtradb-cluster-operator:main-proxysql',
            name: 'IMAGE_PROXY')
        string(
            defaultValue: '',
            description: 'PXC haproxy image: perconalab/percona-xtradb-cluster-operator:main-haproxy',
            name: 'IMAGE_HAPROXY')
        string(
            defaultValue: '',
            description: 'Backup image: perconalab/percona-xtradb-cluster-operator:main-pxc8.0-backup',
            name: 'IMAGE_BACKUP')
        string(
            defaultValue: '',
            description: 'PMM image: perconalab/percona-xtradb-cluster-operator:main-pmm',
            name: 'IMAGE_PMM')
        string(
            defaultValue: '',
            description: 'PXC logcollector image: perconalab/percona-xtradb-cluster-operator:main-logcollector',
            name: 'IMAGE_LOGCOLLECTOR')
    }
    agent {
        label 'docker'
    }
    options {
        skipDefaultCheckout()
        disableConcurrentBuilds()
    }

    stages {
        stage('Prepare') {
            steps {
                git branch: 'master', url: 'https://github.com/Percona-Lab/jenkins-pipelines'
                sh """
                    # sudo is needed for better node recovery after compilation failure
                    # if building failed on compilation stage directory will have files owned by docker user
                    sudo git reset --hard
                    sudo git clean -xdf
                    sudo rm -rf source
                    ./cloud/local/checkout $GIT_REPO $GIT_BRANCH
                """
                stash includes: "source/**", name: "sourceFILES"

                installRpms()
                sh '''
                    if [ ! -d $HOME/google-cloud-sdk/bin ]; then
                        rm -rf $HOME/google-cloud-sdk
                        curl https://sdk.cloud.google.com | bash
                    fi

                    source $HOME/google-cloud-sdk/path.bash.inc
                    gcloud components install alpha
                    gcloud components install kubectl

                    curl -s https://get.helm.sh/helm-v3.2.3-linux-amd64.tar.gz \
                        | sudo tar -C /usr/local/bin --strip-components 1 -zvxpf -
                    curl -s -L https://github.com/openshift/origin/releases/download/v3.11.0/openshift-origin-client-tools-v3.11.0-0cbc58b-linux-64bit.tar.gz \
                        | sudo tar -C /usr/local/bin --strip-components 1 --wildcards -zxvpf - '*/oc'
                    sudo sh -c "curl -s -L https://github.com/mikefarah/yq/releases/download/3.3.2/yq_linux_amd64 > /usr/local/bin/yq"
                    sudo chmod +x /usr/local/bin/yq
                '''
                withCredentials([file(credentialsId: 'cloud-secret-file', variable: 'CLOUD_SECRET_FILE')]) {
                    sh '''
                        cp $CLOUD_SECRET_FILE ./source/e2e-tests/conf/cloud-secret.yml
                    '''
                }
            }
        }
        stage('Build docker image') {
            steps {
                unstash "sourceFILES"
                withCredentials([usernamePassword(credentialsId: 'hub.docker.com', passwordVariable: 'PASS', usernameVariable: 'USER')]) {
                    sh '''
                        if [ -n "${PXC_OPERATOR_IMAGE}" ]; then
                            echo "SKIP: Build is not needed, PXC operator image was set!"
                        else
                            cd ./source/
                            sg docker -c "
                                docker login -u '${USER}' -p '${PASS}'
                                export IMAGE=perconalab/percona-xtradb-cluster-operator:$GIT_BRANCH
                                ./e2e-tests/build
                                docker logout
                            "
                            sudo rm -rf ./build
                        fi
                    '''
                }
            }
        }
        stage('Run Tests') {
            environment {
                CLOUDSDK_CORE_DISABLE_PROMPTS = 1
                GIT_SHORT_COMMIT = sh(script: 'git -C source rev-parse --short HEAD', , returnStdout: true).trim()
                VERSION = "${env.GIT_BRANCH}-${env.GIT_SHORT_COMMIT}"
                CLUSTER_NAME = sh(script: "echo jenkins-latest-pxc-${GIT_SHORT_COMMIT} | tr '[:upper:]' '[:lower:]'", , returnStdout: true).trim()
            }
            parallel {
                stage('E2E Upgrade') {
                    steps {
                        CreateCluster('upgrade')
                        runTest('upgrade-haproxy', 'upgrade')
                        ShutdownCluster('upgrade')
                        CreateCluster('upgrade')
                        runTest('upgrade-proxysql', 'upgrade')
                        ShutdownCluster('upgrade')
                        CreateCluster('upgrade')
                        runTest('smart-update', 'upgrade')
                        runTest('upgrade-consistency', 'upgrade')
                        ShutdownCluster('upgrade')
                    }
                }
                stage('E2E Basic Tests') {
                    steps {
                        CreateCluster('basic')
                        runTest('init-deploy', 'basic')
                        runTest('limits', 'basic')
                        runTest('monitoring-2-0', 'basic')
                        runTest('affinity', 'basic')
                        runTest('one-pod', 'basic')
                        runTest('auto-tuning', 'basic')
                        runTest('proxysql-sidecar-res-limits', 'basic')
                        runTest('users', 'basic')
                        runTest('haproxy', 'basic')
                        runTest('tls-issue-self', 'basic')
                        runTest('tls-issue-cert-manager', 'basic')
                        runTest('tls-issue-cert-manager-ref', 'basic')
                        runTest('validation-hook', 'basic')
                        ShutdownCluster('basic')
                    }
                }
                stage('E2E Scaling') {
                    steps {
                        CreateCluster('scaling')
                        runTest('scaling', 'scaling')
                        runTest('scaling-proxysql', 'scaling')
                        runTest('security-context', 'scaling')
                        ShutdownCluster('scaling')
                    }
                }
                stage('E2E SelfHealing') {
                    steps {
                        CreateCluster('selfhealing')
                        runTest('storage', 'selfhealing')
                        runTest('self-healing', 'selfhealing')
                        runTest('self-healing-advanced', 'selfhealing')
                        runTest('operator-self-healing', 'selfhealing')
                        ShutdownCluster('selfhealing')
                    }
                }
                stage('E2E Backups') {
                    steps {
                        CreateCluster('backups')
                        runTest('recreate', 'backups')
                        runTest('restore-to-encrypted-cluster', 'backups')
                        runTest('demand-backup', 'backups')
                        runTest('demand-backup-encrypted-with-tls', 'backups')
                        runTest('scheduled-backup', 'backups')
                        runTest('pitr','backups')
                        ShutdownCluster('backups')
                    }
                }
                stage('E2E BigData') {
                    steps {
                        CreateCluster('bigdata')
                        runTest('big-data', 'bigdata')
                        ShutdownCluster('bigdata')
                    }
                }
            }
        }
    }
    post {
        always {
            makeReport()
            sh """
                echo "${TestsReport}" > TestsReport.xml
            """
            step([$class: 'JUnitResultArchiver', testResults: '*.xml', healthScaleFactor: 1.0])
            archiveArtifacts '*.xml'

            if (currentBuild.result != null && currentBuild.result != 'SUCCESS') {
                slackSend channel: '#cloud-dev-ci', color: '#FF0000', message: "[${JOB_NAME}]: build ${currentBuild.result}, ${BUILD_URL}"
                slackSend channel: '@${OWNER_SLACK}', color: '#FF0000', message: "[${JOB_NAME}]: build ${currentBuild.result}, ${BUILD_URL}"
            }

            withCredentials([string(credentialsId: 'GCP_PROJECT_ID', variable: 'GCP_PROJECT'), file(credentialsId: 'gcloud-alpha-key-file', variable: 'CLIENT_SECRET_FILE')]) {
                sh '''
                    export CLUSTER_NAME=$(echo jenkins-latest-pxc-$(git -C source rev-parse --short HEAD) | tr '[:upper:]' '[:lower:]')
                    source $HOME/google-cloud-sdk/path.bash.inc
                    gcloud auth activate-service-account alpha-svc-acct@"${GCP_PROJECT}".iam.gserviceaccount.com --key-file=$CLIENT_SECRET_FILE
                    gcloud config set project $GCP_PROJECT
                    gcloud container clusters list --format='csv[no-heading](name)' --filter $CLUSTER_NAME | xargs gcloud container clusters delete --zone us-central1-a --quiet || true
                '''
            }
            sh '''
                sudo docker system prune -fa
                sudo rm -rf ./*
                sudo rm -rf $HOME/google-cloud-sdk
            '''
            deleteDir()
        }
    }
}
