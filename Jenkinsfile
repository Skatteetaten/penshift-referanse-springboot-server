#!/usr/bin/env groovy
def config = [
    scriptVersion              : 'v7',
    iqOrganizationName         : 'Team AOS',
    pipelineScript             : 'https://git.aurora.skead.no/scm/ao/aurora-pipeline-scripts.git',
    downstreamSystemtestJob    : [branch: env.BRANCH_NAME],
    credentialsId              : "github",
    javaVersion                : 11,
    nodeVersion                : '10',
    jiraFiksetIKomponentversjon: true,
    openShiftBuilderVersion: 'feature_AOT_999_layer_builds-SNAPSHOT',
    compileProperties          : "-U",
    versionStrategy            : [
        [branch: 'master', versionHint: '4'],
        [branch: 'release/v3', versionHint: '3'],
        [branch: 'release/v2', versionHint: '2'],
        [branch: 'release/v1', versionHint: '1']
    ]
]
fileLoader.withGit(config.pipelineScript, config.scriptVersion) {
  jenkinsfile = fileLoader.load('templates/leveransepakke')
}
jenkinsfile.maven(config.scriptVersion, config)
