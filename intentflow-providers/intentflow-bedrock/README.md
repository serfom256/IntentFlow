# intentflow-bedrock

Connects IntentFlow to models in Amazon Bedrock through the Converse API of the AWS SDK for Java: Claude, Llama,
Mistral, Amazon Nova and the other chat models in your AWS account, with IAM credentials.

```kotlin
implementation("io.github.serfom256:intentflow-bedrock:0.3.5")
```

```yaml
intentflow:
  llm:
    provider: bedrock
    model: <model or inference profile id>
    bedrock:
      region: eu-central-1
```

| Setting                                | Default            | Meaning                                                    |
| -------------------------------------- | ------------------ | ---------------------------------------------------------- |
| intentflow.llm.model                   | -                  | Model id or inference profile id, as listed in the Bedrock console |
| intentflow.llm.bedrock.region          | AWS default chain  | Region of the model                                        |
| intentflow.llm.bedrock.temperature     | the model's        | Sampling temperature; leave it unset for current Claude models, which reject it |
| intentflow.llm.bedrock.prompt-caching  | false              | Add a cache point after the catalog prompt, for models that support Bedrock prompt caching (Claude, Nova) |
| intentflow.llm.max-tokens              | 16000              | Most tokens one answer may use                             |

Credentials come from the AWS default chain: environment variables, the shared profile, web identity on EKS, or
the instance or task role. The health check confirms that credentials resolve; it does not call the model. Retries
and the timeout come from `intentflow.llm.max-retries` and `intentflow.llm.timeout`.
