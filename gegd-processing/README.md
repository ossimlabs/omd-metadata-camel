# SkySat Processing
Micronaut Deployment of Apache Camel for processing incoming GEGD data.

---

### Routes
This camel hump is the second of two stages in processing GEGD data.
After the first stage, zipped imagery files will be placed into a
temprary directory for this stage to pick up and process

1. Unzip imagery files into `unzipDirectory` for processing.
2. Grab `metadata.json` files and move all files with matching image id's into sorted directory.
3. Create `.omd` files for each desired imagery files. *(ie. `ntf` files)*
4. Look for `.omd` files and send POST to omar stager for each corresponding imagery file. 
5. Cross the Sahara Desert

---

### Configmap Variables

Variable     | Description
------------ | -----------
`app.post.url.prefix` | *Everything before the filename for the stager POST url*
`app.post.url.suffix` | *Everything after the filename for the stager POST url*
`app.mounts` | *Contains the bucket specific information for the ingest workflow*
`app.parsing` | *Contains lists and maps for parsing the `metadata.json` files to create omd files*
---