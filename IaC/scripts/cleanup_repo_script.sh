#!/bin/bash

usage () {
    cat <<EOF
Usage: $0 [OPTIONS]
    The following options may be given :
        --mode              Pass \"latest\" if you want to remove all packages except latest.
                            Pass \"date\" if you want to remove by specific date
        --older_than        Pass days older than packages should be removed. For example: 365
        --repo              Pass name of repository which needed to be cleaned up
        --component         Pass name of component, supported values: \"laboratory\" \"experimental\" \"testing\" \"all\"
        --dry-run           Enables dry-run mode, will show what will be removed and what was excluded only
        --help) usage ;;
Example $0 --mode=latest
EOF
        exit 1
}

parse_arguments() {
    if test "$1" = PICK-ARGS-FROM-ARGV
    then
        shift
    fi

    for arg do
        val=$(echo "$arg" | sed -e 's;^--[^=]*=;;')
        case "$arg" in
            --mode=*) MODE="$val" ;;
            --older_than=*) OLDER_THAN="$val" ;;
            --repo=*) REPO_LIST="$val" ;;
            --component=*) REPO_COMPONENT="$val" ;;
            --dry-run=*) DRY_RUN="$val" ;;
            --help|*) usage ;;
        esac
    done
}

function main {
    if [[ "$REPO_COMPONENT" == "all" ]]; then
        REPO_COMPONENT="testing laboratory experimental"
    fi

    for REPO in $REPO_LIST; do
        for COMPONENT in $REPO_COMPONENT; do
            REPOPATH="/srv/repo-copy/${REPO}/yum/${COMPONENT}"

            echo "Cleaning repo: ${REPO} & component: ${COMPONENT}"

            if [[ ${MODE} == "date" ]]; then
                EXCLUDE_COMPONENT=$(echo "$EXCLUDE_LIST" | grep -o "${COMPONENT}=.*" | awk -F '=' '{print $2}')
                echo "Searching for packages to be excluded across all EL versions"

                for package in $EXCLUDE_COMPONENT; do
                    find $REPOPATH/ -type f -name "$package.*" > /tmp/excludes_${REPO}_${COMPONENT}
                done
                EXCLUDE="$(cat /tmp/excludes_${REPO}_${COMPONENT} | awk -F "/" '{print $NF}')"

                if [[ ${DRY_RUN} == "no" ]]; then
                    FIND_ARG="rm -f"
                else
                    FIND_ARG="echo"
                        printf "Will be excluded: $EXCLUDE \n"
                        printf "Will be removed: \n"
                fi
                find $REPOPATH/ -type f -name "*.rpm" $(printf "! -name %s " $(printf "! -name %s " $EXCLUDE)) -mtime +$OLDER_THAN -exec $FIND_ARG {} \;
                rm /tmp/excludes_${REPO}_${COMPONENT}
            elif [[ ${MODE} == "latest" ]]; then
                echo "Building list of latest packages for ${REPO} in ${COMPONENT}"
                find $REPOPATH/ -type f -name "*.rpm" > /tmp/packages_${REPO}_${COMPONENT}

                echo "Getting names of packages"
                for package in $(cat /tmp/packages_${REPO}_${COMPONENT}); do
                    rpm -qp --nosignature "%{NAME}\n" $package >> /tmp/packages_without_version_${REPO}_${COMPONENT}
                done
                echo "Sorting list of packages without versions"
                cat /tmp/packages_without_version_${REPO}_${COMPONENT} | sort | awk '!seen[$0]++' > /tmp/sorted_packages_without_version_${REPO}_${COMPONENT}
                mv /tmp/sorted_packages_without_version_${REPO}_${COMPONENT} /tmp/packages_without_version_${REPO}_${COMPONENT}

                for EL in el5 el6 el7 el8 src; do
                    EXCLUDE_LATEST=""
                    for package in $(cat /tmp/packages_without_version_${REPO}_${COMPONENT}); do
                        package_path=$(cat /tmp/packages_${REPO}_${COMPONENT} | grep ${EL} | grep "${package}-[0-9]" | sort -r | head -n1)
                        if ! $(echo $EXCLUDE_LATEST | grep -q "${package}-[0-9]"); then
                            EXCLUDE_LATEST+=" $(echo $package_path | awk -F "/" '{print $NF}')"
                        fi
                    done
                    echo $EXCLUDE_LATEST >> /tmp/excludes_${REPO}_${COMPONENT}_${EL}

                    EXCLUDE=$(cat /tmp/excludes_${REPO}_${COMPONENT}_${EL})
                    
                    if [[ ${DRY_RUN} == "yes" ]]; then
                        FIND_ARG="echo"
                        printf "Will be excluded for ${EL} and ${COMPONENT}: $EXCLUDE \n"
                        printf "Will be removed for ${EL} and ${COMPONENT}: \n"
                    else
                        echo "Removing packages except latest versions in ${REPO} in ${COMPONENT} for ${EL}"
                        FIND_ARG="rm -f"
                    fi
                    if [[ ${EL} == 'src' ]]; then
                        find $REPOPATH/ -type f -name "*.src.rpm" $(printf "! -name %s " $(printf "! -name %s " $EXCLUDE)) -exec $FIND_ARG {} \;
                    else
                        find $REPOPATH/ -type f -name "*.${EL}.*.rpm" $(printf "! -name %s " $(printf "! -name %s " $EXCLUDE)) -exec $FIND_ARG {} \;
                    fi
                done
            fi

            if [[ ${DRY_RUN} == "no" ]]; then
                for RHEL_ARCH in $(find ${REPOPATH}/ -maxdepth 3 -type d \( -name "*x86_64" -o -name "*noarch" -o -name "i386" -o -name "SRPMS" \)); do
                    if [ -f ${RHEL_ARCH}/repodata/repomd.xml.asc ]; then
                        rm -f  ${RHEL_ARCH}/repodata/repomd.xml.asc
                    fi
                    gpg --detach-sign --armor --passphrase $PASSWORD ${RHEL_ARCH}/repodata/repomd.xml
                    createrepo --update ${RHEL_ARCH}/
                done
            fi

            rm /tmp/excludes_${REPO}_${COMPONENT}_${EL} /tmp/packages_without_version_${REPO}_${COMPONENT}
        done
    done
}

REPO_COMPONENT="all"
REPO_LIST="tools"
OLDER_THAN="365"
MODE="latest"
DRY_RUN="no"

EXCLUDE_LIST="
experimental=percona-xtrabackup-test-80-8.0.5-1 percona-backup-mongodb-agent-0.5.0-1
testing=patchelf-0.12-2
laboratory= 
"

parse_arguments PICK-ARGS-FROM-ARGV "$@"

main