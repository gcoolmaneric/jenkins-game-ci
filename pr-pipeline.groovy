@NonCPS
import groovy.json.JsonSlurper

// Parse request payload from Gtihub's webhook
def parseJSON(text) {
    return new groovy.json.JsonSlurperClassic().parseText(text)
}

// Get localtime
def getLocalTime() {
  Date date = new Date()
  String datePart = date.format("yyyyMMdd")
  String timePart = date.format("HHmmss")
  return datePart + timePart
}

// Send Slack Message
def sendSlackMessage(String buildStatus = 'STARTED', String github_user = '', String job_name = '', String pull_req_url = '', String branch_name = '', String download_url = '') {
    // Build status of null means success.
    buildStatus = buildStatus ?: 'SUCCESS'

    def color
    def hoge

    if (buildStatus == 'STARTED') {
        color = '#D4DADF'
    } else if (buildStatus == 'SUCCESS') {
        color = '#BDFFC3'
    } else if (buildStatus == 'UNSTABLE') {
        color = '#FFFE89'
    } else {
        color = '#FF9FA1'
    }

    def msg = "Pull Request *TEST ${buildStatus}*\n Pipeline: ${job_name} \n User: ${github_user} \n Branch: <${pull_req_url}|${branch_name}>\n Jenkins job : <${env.BUILD_URL}|#${env.BUILD_NUMBER}>\n Download Android:\n${download_url}"
    if (download_url == '') {
        msg = "Pull Request *TEST ${buildStatus}*\n Pipeline: ${job_name} \n User: ${github_user} \n Branch : <${pull_req_url}|${branch_name}>\n Jenkins job : <${env.BUILD_URL}|#${env.BUILD_NUMBER}>\n"
    }

    // ex: #game_ci_test
    slackSend(color: color, message: msg, channel: "<Your Slack Channel Name>")
}

// Parse request payload from webhook
def json = parseJSON(payload)

// Valudate pull_request
if (json.pull_request == null) {
    println("pull_request is null")
    return
}
// Dump pull_request
def url = json.pull_request.url
println("url: "+ url)

def json_number = json.number
println("json_number: "+ json_number)

def timeStamp=getLocalTime()
println("timeStamp: "+ timeStamp)

def json_branch = json.pull_request.head.ref
println("json_branch: "+ timeStamp)

def json_state = json.pull_request.state
println("json_state: "+ json_state)

def json_html = json.pull_request.html_url

def user_name=""
if (json.pull_request.user.login != null) {
  user_name = json.pull_request.user.login
}
println("user_name: "+ user_name)

// Validate PR's state must be open
if( "${json_state}" != "open" ) {
    echo "Pull request " + "${json_state}" + "."
    currentBuild.result = 'SUCCESS'
    return
}

// Distributed URL via https://distribution.com
def DEPLOY_URL=""
// deploy_gate_token
def deploy_gate_token="<deploy_gate_token>"
// deploy_gate_upload url
def deploy_gate_upload_url="https://deploygate.com/api/users/<deploy_group_name>/apps"

// Pipeline name
def jobName="your-game-pr"

// Machine's user Name
def machine_user_name="your_user_name"

// Your Github URL
def build_repo="git@github.com:gcoolmaneric/jenkins-game-ci.git"

// Your Unity Version
// Ex: "2020.1.15f2"
def unity_version="<Your unity verison>"


pipeline {
   // Master Jenkins
   agent any

   // Initialize environment params
   environment{
       UNITY_PATH="/Applications/Unity/Hub/Editor/${unity_version}/Unity.app/Contents/MacOS/Unity"
       deployGate_token="${deploy_gate_token}"
       deployGate_upload_url="${deploy_gate_upload_url}"
       app_mode="mono"  // mono or IL2CPP
       repo="${build_repo}"
       branch="${json_branch}"
       pr_id="${json_number}"
       workingDir="/Users/${machine_user_name}/.jenkins/workspace/${json_branch}"
   }

    stages {
        stage('Clean up working space') {
            steps {
                sh  """rm -rf ${workingDir}/${branch}\
                """
            }
        }

        stage('Clone Branch') {
            steps {
              sh """git clone --branch ${branch} --depth 1 ${repo} ${workingDir}/${branch};\
                  cd ${workingDir}/${branch};\
                """
            }
        }

        stage('PlayMode Test') {
            steps {
              sh """cd ${workingDir}/${branch};\
                  ${UNITY_PATH} -batchmode -projectPath ${workingDir}/${branch} -runTests -testResults ${workingDir}/${branch}/CI/results.xml -testPlatform PlayMode -nographics -quit;\
                """
            }
        }

        stage('Build Android') {
            steps {
              script {
                   // BuildIL2CPPAndroid
                   if( "${app_mode}" != "mono" ) {
                     sh """cd ${workingDir}/${branch};\
                         mkdir builds
                         ${UNITY_PATH} -batchmode -projectPath ${workingDir}/${branch} -buildTarget Android -executeMethod BuilderUtility.BuildIL2CPPAndroid -nographics -quit;\
                         mv test-app.apk ./builds
                       """
                   } else {
                     // BuildMonoAndroid
                     sh """cd ${workingDir}/${branch};\
                         mkdir builds
                         ${UNITY_PATH} -batchmode -projectPath ${workingDir}/${branch} -buildTarget Android -executeMethod BuilderUtility.BuildMonoAndroid -nographics -quit;\
                         mv test-app.apk ./builds
                       """
                   }
               }
            }
        }

       //  stage('Upload App to DeployGate') {
       //     steps {
       //         script {
       //             JSON_DATA = sh (
       //                 script: "curl -H 'Authorization: token ${deployGate_token}' -F 'file=@${workingDir}/${branch}/builds/test-app.apk' -F 'distribution_name=${branch}' '${deployGate_upload_url}'",
       //                 returnStdout: true
       //             ).trim()
       //             echo  "JSON_DATA: ${JSON_DATA}"
       //
       //             def slurper = new JsonSlurper()
       //             DEPLOY_URL = slurper.parseText(JSON_DATA).results.distribution.url
       //             echo  "DEPLOY_URL: ${DEPLOY_URL}"
       //         }
       //     }
       // }
    }

   post {
       success {
           sendSlackMessage("SUCCESS",user_name, jobName, url, json_branch, DEPLOY_URL)
       }
       //triggered when red sign
       failure {
           sendSlackMessage("FAILURE",user_name, jobName, url, json_branch, DEPLOY_URL)
       }
   }
}
