@Library('deploy')
import deploy

def deployLib = new deploy()

// Obtain ephemeral "installation" API token which can be used for GitHub repository access.
// A token is valid for one hour after creation.
githubAppId = '23179'
githubAppCredentialId = 'teampam-ci'
def newApiToken() {
    withEnv(['HTTPS_PROXY=webproxy-internett.nav.no:8088']) {
        withCredentials([file(credentialsId: githubAppCredentialId, variable: 'KEYFILE')]) {
            dir('token') {
                def generatedToken = sh(script: "generate-jwt.sh \$KEYFILE ${githubAppId} | xargs generate-installation-token.sh", returnStdout: true)
                return generatedToken.trim()
            }
        }
    }
}

node {
    def application = "pam-ontology-label-parser"

    def committer, committerEmail, changelog, releaseVersion, nextVersion, isSnapshot // metadata

    def mvnHome = tool "maven-3.3.9"
    def mvn = "${mvnHome}/bin/mvn"
    def repo = "navikt"
    def githubAppToken = newApiToken();

    def color

    try {

        stage("checkout") {
            cleanWs()
            withEnv(['HTTPS_PROXY=http://webproxy-internett.nav.no:8088']) {
                // githubAppToken is not a magic secret variable, so mask it manually by disabling shell echo
                // Would be great if withCredentials could be used to mask the value, mark it as secret, or similar
                println("Repository URL is https://x-access-token:****@github.com/${repo}/${application}.git")
                sh(script: "set +x; git clone https://x-access-token:${githubAppToken}@github.com/${repo}/${application}.git .")
            }
        }

        stage("initialize") {
            pom = readMavenPom file: 'pom.xml'
            releaseVersion = pom.version.tokenize("-")[0]
            isSnapshot = pom.version.contains("-SNAPSHOT")
            committer = sh(script: 'git log -1 --pretty=format:"%an (%ae)"', returnStdout: true).trim()
            committerEmail = sh(script: 'git log -1 --pretty=format:"%ae"', returnStdout: true).trim()
            changelog = sh(script: 'git log `git describe --tags --abbrev=0`..HEAD --oneline', returnStdout: true)
            currentBuild.displayName = releaseVersion
        }

        stage("verify maven versions") {
            sh 'echo "Verifying that no snapshot dependencies is being used."'
            sh 'grep module pom.xml | cut -d">" -f2 | cut -d"<" -f1 > snapshots.txt'
            sh 'echo "./" >> snapshots.txt'
            sh 'while read line;do if [ "$line" != "" ];then if [ `grep SNAPSHOT $line/pom.xml | wc -l` -gt 1 ];then echo "SNAPSHOT-dependencies found. See file $line/pom.xml.";exit 1;fi;fi;done < snapshots.txt'
        }

        stage("build and test backend") {
            if (isSnapshot) {
                sh "${mvn} clean install -Dit.skip=true -Djava.io.tmpdir=/tmp/${application} -B -e"
            } else {
                println("POM version is not a SNAPSHOT, it is ${pom.version}. Skipping build and testing of backend")
            }

        }

        stage("release version") {
            withEnv(['HTTPS_PROXY=http://webproxy-internett.nav.no:8088']) {
                sh "${mvn} versions:set -B -DnewVersion=${releaseVersion} -DgenerateBackupPoms=false"
                sh "git commit -am \"set version to ${releaseVersion} (from Jenkins pipeline)\""
                sh ("git push -q origin")
                sh ("git tag -a ${releaseVersion} -m ${releaseVersion}")
                sh ("git push -q origin --tags")
            }
        }

        stage("publish artifact") {
            withCredentials([usernamePassword(credentialsId: 'deployer', usernameVariable: 'DEP_USERNAME', passwordVariable: 'DEP_PASSWORD')]) {
                if (isSnapshot) {
                    sh "${mvn} clean deploy -Dusername=${env.DEP_USERNAME} -Dpassword=${env.DEP_PASSWORD} -DskipTests -B -e"
                } else {
                    println("POM version is not a SNAPSHOT, it is ${pom.version}. Skipping publishing!")
                }
            }
        }

        stage("new dev version") {
            withEnv(['HTTPS_PROXY=http://webproxy-internett.nav.no:8088']) {
                def v = releaseVersion.tokenize(".")
                v[v.size()-1] = v[v.size()-1].toInteger()+1
                nextVersion = v.join(".") + "-SNAPSHOT"
                sh "${mvn} versions:set -B -DnewVersion=${nextVersion} -DgenerateBackupPoms=false"
                sh "git commit -am \"updated to new dev-version ${nextVersion} after release by ${committer}\""
                sh "git push -q origin"
            }
        }

        color = '#BDFFC3'
        GString message = ":heart_eyes_cat: Siste commit på ${application} bygd OK.\nSiste commit ${changelog}"
        slackSend color: color, channel: '#pam_bygg', message: message, teamDomain: 'nav-it', tokenCredentialId: 'pam-slack'


    } catch (e) {
        color = '#FF0004'
        GString message = ":crying_cat_face: :crying_cat_face: :crying_cat_face: :crying_cat_face: :crying_cat_face: :crying_cat_face: Help sad cat! \n Siste commit på ${application} gikk ikkje gjennom. Sjå logg for meir info ${env.BUILD_URL}\nLast commit ${changelog}"
        slackSend color: color, channel: '#pam_bygg', message: message, teamDomain: 'nav-it', tokenCredentialId: 'pam-slack'
    }

}
