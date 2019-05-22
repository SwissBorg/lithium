pipeline {

    agent {
        docker {
            image 'docker.sharedborg.com/sbt-j11-build:128-3.4'
            args '-v ${HOME}/.ivy2:${WORKSPACE}/.ivy2 -v ${HOME}/.sbt:${WORKSPACE}/.sbt -u root --privileged -m4G --cpus 2'
        }
    }

    environment {
        SBT_OPTS = '-Xmx1G -Duser.home=.'
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
                    sh 'sbt test multi-jvm:test'
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