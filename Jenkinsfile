pipeline {

    agent {
        docker {
            image 'sbt-j11-build:128-5.8'
            args '-v /var/run/docker.sock:/var/run/docker.sock -v /var/tmp/.ivy2:${WORKSPACE}/.ivy2 -v /var/tmp/.sbt:${WORKSPACE}/.sbt -v /var/tmp/.coursier:${WORKSPACE}/.cache/coursier -m8G --cpus 4 --group-add docker'
            registryUrl 'https://dockerpush.sharedborg.com'
            registryCredentialsId 'nexus_deployer'
        }
    }

    environment {
        /* -Duser.home=. is needed to have it all work in the docker image above */
        SBT_OPTS = '-Duser.home=.'
        NEXUS = credentials('nexus_deployer')
    }

    options {
        ansiColor('xterm')
        gitLabConnection('SB_GitLab_cloud')
        gitlabBuilds(builds: ['build', 'test', 'publish'])
    }

    stages {

        stage('Build') {
            steps {
                gitlabCommitStatus(name: 'build') {
                    sh 'sbt scalafmtCheck test:scalafmtCheck compile test:compile'
                }
            }
        }

        stage('Test') {
            steps {
                gitlabCommitStatus(name: 'test') {
                    sh 'sbt test multi-jvm:test '
                }
            }
        }

        stage('Publish') {
            steps {
                gitlabCommitStatus(name: 'publish') {
                    sh 'sbt publish'
                }
            }
        }

    }

    post {
        always {
            junit 'target/test-reports/*.xml'
        }
        cleanup {
            cleanWs()
        }
    }

}