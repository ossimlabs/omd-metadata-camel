
# unzip-and-ingest

  

#### Purpose:

Watches an ingest directory for zip files and stages the imagery files.

  
  

#### Deploying:

  

###### Requirements

*  [Helm](https://helm.sh/docs/intro/install/)

###### Optional

*  [Skaffold](https://skaffold.dev/docs/install/)

  

_Note_: The application can be deployed using vanilla Helm, or Skaffold which uses Helm under the hood. Skaffold

allows for rapid deployment capabilities while developing.


###### Mandatory Steps

1. Log into Kubernetes environment: `gimme-aws-creds`

2. Log into Docker: `docker login <DOCKER_REPOSITORY>`

  

###### Helm only

`helm install <HELM_DEPLOY_NAME> chart`

  

###### Skaffold with Helm

  

`skaffold dev` Uses Helm to deploy to Kubernetes. However, Skaffold is set to monitor any code or config changes, and

automatically redeploys the application with the new changes.

  

`skaffold run` Uses Helm to deploy to Kubernetes. This is the same as the vanilla Helm install.

---
### `.omd` file construction
`ProcessFilesProcessor.groovy` (PFP) handles the logic for writing `omd` files for incoming imagery.

PFP looks in imagery metadata files for key/values which need to be written to an `omd` file. This logic is generic and not specific to imagery type. 

PFP will use `omdKeyMapList` config object to check for `oldKeys` matched in each metadata file. If a match is found, check if there is a matching `oldValue`, if so, construct a string for the omd file: `"${omdKeyMapList.key}:${keyVal}\n"`

```
omdKeyMapList:
  - key: mission_id // key to replace matched keys
    oldKeys: // keys in metadata.json
      - provider
      - sensorName
    values:
      - oldValue: gegd // old value in metadata
        newValue: SkySat // replacement for omd file
```