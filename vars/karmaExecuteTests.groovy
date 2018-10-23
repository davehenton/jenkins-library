import com.sap.piper.ConfigurationHelper
import com.sap.piper.GitUtils
import com.sap.piper.Utils

import groovy.text.SimpleTemplateEngine
import groovy.transform.Field

@Field String STEP_NAME = 'karmaExecuteTests'
@Field Set STEP_CONFIG_KEYS = [
    'containerPortMappings', //port mappings required for containers. This will only take effect inside a Kubernetes pod, format [[containerPort: 1111, hostPort: 1111]]
    'dockerEnvVars', //envVars to be set in the execution container if required
    'dockerImage', //Docker image for code execution
    'dockerName', //name of the Docker container. This will only take effect inside a Kubernetes pod.
    'dockerWorkspace', //user home directory for Docker execution. This will only take effect inside a Kubernetes pod.
    'failOnError',
    'installCommand',
    'modules',
    'sidecarEnvVars', //envVars to be set in Selenium container if required
    'sidecarImage', //image for Selenium execution which runs as sidecar to dockerImage
    'sidecarName', //name of the Selenium container. If not on Kubernetes pod, this will define the name of the link to the Selenium container and is thus required for accessing the server, example http://selenium:4444 (default)
    'sidecarVolumeBind', //volume bind. This will not take effect in Kubernetes pod.
    'stashContent', //list of stash names which are required to be unstashed before test run
    'testCommand'
]
@Field Set PARAMETER_KEYS = STEP_CONFIG_KEYS

void call(Map parameters = [:]) {
    handlePipelineStepErrors(stepName: STEP_NAME, stepParameters: parameters) {
        def script = parameters?.script ?: [commonPipelineEnvironment: commonPipelineEnvironment]
        def utils = parameters?.juStabUtils ?: new Utils()

        // load default & individual configuration
        Map config = ConfigurationHelper.newInstance(this)
            .loadStepDefaults()
            .mixinGeneralConfig(script.commonPipelineEnvironment, STEP_CONFIG_KEYS)
            .mixinStepConfig(script.commonPipelineEnvironment, STEP_CONFIG_KEYS)
            .mixinStageConfig(script.commonPipelineEnvironment, parameters.stageName?:env.STAGE_NAME, STEP_CONFIG_KEYS)
            .mixin(parameters, PARAMETER_KEYS)
            .use()

        utils.pushToSWA([step: STEP_NAME], config)

        def testJobs = [:]
        def options = [
            buildTool: 'npm',
            containerPortMappings: config.containerPortMappings,
            dockerEnvVars: config.dockerEnvVars,
            dockerImage: config.dockerImage,
            dockerName: config.dockerName,
            dockerWorkspace: config.dockerWorkspace,
            failOnError: config.failOnError,
            sidecarEnvVars: config.sidecarEnvVars,
            sidecarImage: config.sidecarImage,
            sidecarName: config.sidecarName,
            sidecarVolumeBind: config.sidecarVolumeBind,
            stashContent: config.stashContent
        ]
        for(String path : config.modules){
            testJobs["Karma - ${path}"] = {
                seleniumExecuteTests(options){
                    sh "cd '${path}' && ${config.installCommand}"
                    sh "cd '${path}' && ${config.runCommand}"
                }
            }
        }

        if(testJobs.size() == 1){
            testJobs.each({ key, value -> value() })
        }else{
            parallel testJobs.plus([failFast: false])
        }
    }
}