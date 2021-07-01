def call() {
    sh '''
        SYSREL=$(cat /etc/system-release | tr -dc '0-9.'|awk -F'.' {'print $1'})
        if [[ $SYSREL -eq 2 ]]; then
            sudo amazon-linux-extras install epel -y
            sudo yum install -y p7zip
        elif [[ $SYSREL -eq 7 ]]; then
            sudo yum install -y epel-release 
            sudo yum install -y p7zip
        fi

        sudo rm -rf /tmp/aws* || true
        until curl "https://awscli.amazonaws.com/awscli-exe-linux-x86_64.zip" -o "/tmp/awscliv2.zip"; do
            sleep 1
            echo try again
        done
        7za -aoa -o/tmp x /tmp/awscliv2.zip 
        cd /tmp/aws && sudo ./install
        sudo rm -rf /tmp/aws* || true
    '''
}
