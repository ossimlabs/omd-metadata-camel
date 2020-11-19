{{- define "unzip-and-ingest.imagePullSecret" }}
{{- printf "{\"auths\": {\"%s\": {\"auth\": \"%s\"}}}" .Values.global.imagePullSecret.registry (printf "%s:%s" .Values.global.imagePullSecret.username .Values.global.imagePullSecret.password | b64enc) | b64enc }}
{{- end }}

{{/* Template for env vars */}}
{{- define "unzip-and-ingest.envVars" -}}
  {{- range $key, $value := .Values.envVars }}
  - name: {{ $key | quote }}
    value: {{ $value | quote }}
  {{- end }}
{{- end -}}


{{/* Templates for the configMap mounts section */}}

{{- define "unzip-and-ingest.mountBuckets" -}}
{{- range $volumeName := .Values.volumeNames }}
{{- $volumeDict := index $.Values.global.volumes $volumeName }}
- bucket: {{ $volumeDict.mountPath | replace "/" "" }}
  ingestDirectory: ingest
  archiveDirectory: archive
  unzipDirectory: unzipped
{{- end -}}
{{- end -}}



{{/* Templates for the volumeMounts section */}}

{{- define "unzip-and-ingest.volumeMounts.configmaps" -}}
{{- range $configmap := .Values.configmaps}}
- name: {{ $configmap.internalName | quote }}
  mountPath: {{ $configmap.mountPath | quote }}
  {{- if $configmap.subPath }}
  subPath: {{ $configmap.subPath | quote }}
  {{- end }}
{{- end -}}
{{- end -}}

{{- define "unzip-and-ingest.volumeMounts.pvcs" -}}
{{- range $volumeName := .Values.volumeNames }}
{{- $volumeDict := index $.Values.global.volumes $volumeName }}
- name: {{ $volumeName }}
  mountPath: {{ $volumeDict.mountPath }}
  {{- if $volumeDict.subPath }}
  subPath: {{ $volumeDict.subPath | quote }}
  {{- end }}
{{- end -}}
{{- end -}}

{{- define "unzip-and-ingest.volumeMounts" -}}
{{- include "unzip-and-ingest.volumeMounts.configmaps" . -}}
{{- include "unzip-and-ingest.volumeMounts.pvcs" . -}}
{{- end -}}





{{/* Templates for the volumes section */}}

{{- define "unzip-and-ingest.volumes.configmaps" -}}
{{- range $configmap := .Values.configmaps}}
- name: {{ $configmap.internalName | quote }}
  configMap:
    name: {{ $configmap.name | quote }}
{{- end -}}
{{- end -}}

{{- define "unzip-and-ingest.volumes.pvcs" -}}
{{- range $volumeName := .Values.volumeNames }}
{{- $volumeDict := index $.Values.global.volumes $volumeName }}
- name: {{ $volumeName }}
  persistentVolumeClaim:
    claimName: "{{ $.Values.appName }}-{{ $volumeName }}-pvc"
{{- end -}}
{{- end -}}

{{- define "unzip-and-ingest.volumes" -}}
{{- include "unzip-and-ingest.volumes.configmaps" . -}}
{{- include "unzip-and-ingest.volumes.pvcs" . -}}
{{- end -}}
