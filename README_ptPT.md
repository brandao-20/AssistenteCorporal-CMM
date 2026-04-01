# Assistente corporal

Projeto Android em Kotlin para a UC de Computação Móvel e Multissensorial.

## O que integra
- **1 sensor do dispositivo**: Rotation Vector
- **2 use cases CameraX**: Preview + ImageAnalysis
- **1 API do ML Kit**: Pose Detection
- **Ecrã inicial explicativo**
- **Área de aplicação**: apoio ao enquadramento corporal e execução básica de agachamentos

## Decisões principais
- Vista lateral como modo principal
- Câmara traseira por defeito, com botão para trocar para a frontal
- Thresholds de agachamento ajustáveis com botões simples
- Contagem de repetições baseada no ângulo do joelho do lado com melhor visibilidade

## Como abrir
1. Abrir a pasta do projeto no Android Studio.
2. Esperar pela sincronização do Gradle.
3. Ligar um dispositivo Android com câmara.
4. Executar a app.

## Nota técnica
A integração de pose usa `com.google.mlkit:pose-detection:18.0.0-beta5` em modo `STREAM_MODE`.


Compatibilidade ajustada para AGP 8.5.2: compileSdk/targetSdk 34, Gradle 8.7, CameraX 1.3.4, Activity 1.9.3 e Core KTX 1.13.1.
