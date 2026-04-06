# Assistente Corporal

Aplicação Android desenvolvida em **Kotlin** no âmbito da unidade curricular de **Computação Móvel e Multissensorial (CMM)**, com foco no apoio ao enquadramento corporal e na análise básica da execução de **agachamentos em vista lateral**.

## Objetivo

A aplicação foi concebida para ajudar o utilizador a:
- posicionar corretamente o telemóvel;
- manter o corpo visível em perfil lateral;
- verificar se a orientação do dispositivo é adequada;
- acompanhar a profundidade do movimento;
- contar repetições válidas de forma simples.

A app funciona como um **assistente experimental de apoio ao exercício físico**, não substituindo avaliação profissional.

---

## Requisitos da UC cumpridos

A aplicação integra os elementos obrigatórios pedidos no trabalho prático:

- **1 sensor do dispositivo**: Rotation Vector
- **2 use cases do CameraX**:
    - Preview
    - ImageAnalysis
- **1 API do ML Kit**:
    - Pose Detection
- **Ecrã inicial explicativo**
- **Área de aplicação com justificação**
- **Repositório Git**
- **Relatório e vídeo demonstrativo**

---

## Funcionalidades principais

- introdução inicial em mini-slides;
- análise em **vista lateral**;
- preview da câmara em tempo real;
- alternância entre **câmara traseira** e **câmara frontal**;
- deteção da pose com ML Kit;
- guia visual de enquadramento corporal;
- verificação da inclinação do telemóvel;
- leitura do lado visível;
- cálculo do ângulo principal do joelho;
- barra de profundidade;
- contagem de repetições;
- presets rápidos:
    - Permissivo
    - Normal
    - Exigente
- ajuste manual de thresholds;
- persistência local de preferências.

---

## Stack tecnológica

- **Kotlin**
- **Android Studio**
- **ViewBinding**
- **CameraX**
- **ML Kit Pose Detection**
- **Rotation Vector Sensor**
- **XML Layouts**

---

## Estrutura principal do projeto

```text
app/
 └─ src/main/
    ├─ java/com/example/assistentecorporal/
    │  ├─ MainActivity.kt
    │  ├─ AnalysisActivity.kt
    │  ├─ PoseAnalyzer.kt
    │  ├─ ExerciseFeedbackEngine.kt
    │  ├─ DeviceOrientationHelper.kt
    │  ├─ GuidanceOverlayView.kt
    │  └─ AppPreferences.kt
    ├─ res/layout/
    │  ├─ activity_main.xml
    │  └─ activity_analysis.xml
    └─ res/values/
       ├─ strings.xml
       ├─ colors.xml
       └─ themes.xml