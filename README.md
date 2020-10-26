# unzip-and-ingest

#### Purpose:
Watches an ingest directory for zip files and stages the imagery files.


#### Deploying:

###### Requirements
* [Helm](https://helm.sh/docs/intro/install/)
###### Optional
* [Skaffold](https://skaffold.dev/docs/install/)

_Note_: The application can be deployed using vanilla Helm, or Skaffold which uses Helm under the hood.  Skaffold
allows for rapid deployment capabilities while developing.

###### Mandatory Steps
1. Log into Kubernetes environment: `gimme-aws-creds`
2. Log into Docker: `docker login <DOCKER_REPOSITORY>`

###### Helm only
`helm install -f chart/values-dev.yaml <HELM_DEPLOY_NAME> chart`

###### Skaffold with Helm

`skaffold dev` Uses Helm to deploy to Kubernetes. However, Skaffold is set to monitor any code or config changes, and
automatically redeploys the application with the new changes.

`skaffold run` Uses Helm to deploy to Kubernetes.  This is the same as the vanilla Helm install. 
