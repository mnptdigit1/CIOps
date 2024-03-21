library 'ci-libs'

def call(Map pipelineParams) {

podTemplate(yaml: """
kind: Pod
metadata:
  name: egov-deployer
spec:
  containers:
  - name: egov-deployer
    image: egovio/egov-deployer:3-master-931c51ff
    command:
    - cat
    tty: true
    env:  
      - name: "GOOGLE_APPLICATION_CREDENTIALS"
        value: "/var/run/secret/cloud.google.com/service-account.json"              
    volumeMounts:      
      - name: kube-config
        mountPath: /root/.kube     
    resources:
      requests:
        memory: "256Mi"
        cpu: "200m"
      limits:
        memory: "256Mi"
        cpu: "200m"  
  volumes:
  - name: kube-config
    secret:
        secretName: "${pipelineParams.environment}-kube-config"                    
"""
    ) {
        node(POD_LABEL) {

          git url: pipelineParams.repo, branch: pipelineParams.branch, credentialsId: 'git_read'
            
            // Adding the "Export Kubeconfig Secret" stage
            stage('Export Kubeconfig Secret') {
                container(name: 'egov-deployer', shell: '/bin/sh') {
                    sh """
                        # Create the .kube directory
                        mkdir -p /root/.kube
                        
                        # Extract the kubeconfig from the secret and write it to a file
                        kubectl get secret ${pipelineParams.environment}-kube-config -n egov -o jsonpath='{.data.config}' | base64 -d > /root/.kube/config-bkppp
                        
                        # Optionally, set KUBECONFIG environment variable to use this kubeconfig
                        export KUBECONFIG=/root/.kube/config/config-bkppp
                        kubectl config get-contexts
                        kubectl config current-context
                        aws-iam-authenticator version
                        kubectl get nodes
                    """
                }
          
            git url: pipelineParams.repo, branch: pipelineParams.branch, credentialsId: 'git_read'
                stage('Deploy Images') {
                        container(name: 'egov-deployer', shell: '/bin/sh') {
                            sh """
                                /opt/egov/egov-deployer deploy --helm-dir `pwd`/${pipelineParams.helmDir} -c=${env.CLUSTER_CONFIGS}  -e ${pipelineParams.environment} "${env.IMAGES}"
                            """
                            }
                }
        }
    }


}
