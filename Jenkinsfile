pipeline {
    agent any

    tools {
        maven 'Maven-3.9'
    }

    triggers {
        pollSCM('* * * * *')
    }

    stages {

        stage('Checkout') {
            steps {
                echo '📥 Descargando código desde GitHub...'
                checkout scm
            }
        }

        stage('Build') {
            steps {
                echo '🔨 Compilando con Maven...'
                sh 'mvn clean package -DskipTests'
            }
        }

        stage('Deploy a Tomcat') {
            steps {
                echo '🚀 Desplegando en Tomcat...'
                deploy adapters: [
                    tomcat9(
                        credentialsId: 'tomcat-creds',
                        path: '',
                        url: 'http://tomcat:8080'
                    )
                ],
                contextPath: 'mi-app',
                war: '**/*.war'
            }
        }

        stage('Análisis SonarQube') {
            steps {
                echo '🔍 Analizando código con SonarQube...'
                withSonarQubeEnv('SonarQube-Server') {
                    sh '''
                        mvn sonar:sonar \
                          -Dsonar.projectKey=mi-app \
                          -Dsonar.projectName="Mi App"
                    '''
                }
            }
        }

        stage('Quality Gate') {
            steps {
                echo '⏳ Esperando resultado del Quality Gate...'
                timeout(time: 2, unit: 'MINUTES') {
                    waitForQualityGate abortPipeline: true
                }
            }
        }
    }

    post {
        success {
            echo '✅ Pipeline exitoso - Regla cumplida'
            mail(
                to: 'mateber29@gmail.com',
                subject: "✅ [Jenkins] Build #${env.BUILD_NUMBER} - Regla SonarQube CUMPLIDA",
                body: """
El proyecto mi-app pasó exitosamente la regla de SonarQube.

Build: #${env.BUILD_NUMBER}
Branch: ${env.GIT_BRANCH}
URL del build: ${env.BUILD_URL}
SonarQube: http://localhost:9000/dashboard?id=mi-app
                """
            )
        }
        failure {
            echo '❌ Pipeline fallido - Regla no cumplida'
            mail(
                to: 'mateber29@gmail.com',
                subject: "❌ [Jenkins] Build #${env.BUILD_NUMBER} - Regla SonarQube NO CUMPLIDA",
                body: """
El proyecto mi-app NO cumplió la regla de SonarQube.

Build: #${env.BUILD_NUMBER}
Branch: ${env.GIT_BRANCH}
URL del build: ${env.BUILD_URL}
SonarQube: http://localhost:9000/dashboard?id=mi-app

Por favor revise el análisis y corrija el código.
                """
            )
        }
    }
}