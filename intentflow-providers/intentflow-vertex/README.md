# intentflow-vertex

Connects IntentFlow to Gemini through Google's Gen AI SDK (`com.google.genai:google-genai`): on Vertex AI in your
Google Cloud project, or on the Gemini Developer API with an API key.

```kotlin
implementation("io.github.serfom256:intentflow-vertex:0.3.5")
```

```yaml
intentflow:
  llm:
    provider: vertex
    model: <Gemini model id>
    vertex:
      project: my-project
      location: global
```

| Setting                        | Default | Meaning                                                                    |
| ------------------------------ | ------- | -------------------------------------------------------------------------- |
| intentflow.llm.vertex.project  | -       | Google Cloud project; without it the Gemini Developer API is used with `api-key` |
| intentflow.llm.vertex.location | global  | Vertex AI location, e.g. `global`, `us-central1` or `europe-west4`         |
| intentflow.llm.api-key         | -       | Gemini Developer API key, when no project is set                           |
| intentflow.llm.max-tokens      | 16000   | Most tokens one answer may use                                             |

On Vertex AI the SDK signs in with Application Default Credentials: a service account on Google Cloud, workload
identity, or `gcloud auth application-default login` on a laptop. Thinking tokens count as completion tokens in
`trace().tokens()`.
