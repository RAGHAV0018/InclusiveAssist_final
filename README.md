# InclusiveAssist 🤝📱

**InclusiveAssist** is an innovative Android application designed to bridge the communication gap for **Visually Impaired (Blind)** and **Hearing Impaired (Deaf)** individuals. By leveraging advanced **Cloud AI** (Gemini, Llama) and efficient **On-Device ML** (Google ML Kit), it provides real-time intelligent assistance for navigating the world.

---

## 🚀 Key Features

### 👁️ Blind Assistance Mode ("Eyes-Free")
*   **🤖 AI Object Detection**: Uses **Gemini 2.5 Flash** to identify objects in real-time with < 2s latency.
*   **📝 Scene Description**: Generates detailed, context-aware descriptions of surroundings (e.g., "A messy desk with a laptop and coffee mug").
*   **📖 Vision Reader**: Instantly reads printed text offline using **ML Kit**, ensuring privacy and speed.
*   **💵 Currency Checker**:  Identifies currency notes to help with financial independence.
*   **💡 Light Detector**:  Auditory feedback to detect light intensity in a room.

### 👂 Deaf Assistance Mode ("Visual-First")
*   **📢 Sound Alert & Safety**: Monitors environmental noise levels (dB). Triggers **Visual Red Screen + Vibration** for loud sounds (>80dB).
*   **🎓 Classroom Mode**: Real-time continuous transcription of lectures or conversations, displayed as scrolling text.
*   **🗣️ Speech-to-Text**: Bi-directional communication tool. Types what others speak, and speaks what the user types.

### 📍 Common Safety Features
*   **🆘 Emergency SOS**: One-tap access to dial emergency contacts and send WhatsApp location links.
*   **🔔 Destination Alarm**: Wake-up alarm that triggers when you get within 500m of a set destination.

---

## 🛠️ Tech Stack

*   **Platform**: Android (Native Java)
*   **Architecture**: MVC Pattern
*   **AI & Cloud**:
    *   **Google Gemini 2.5 Flash**: Object detection & scene understanding.
    *   **Llama 3 (via Groq)**: Fast conversational AI assistant.
*   **On-Device ML**:
    *   **Google ML Kit**: Text Recognition (OCR).
*   **Sensors & Core**:
    *   `FusedLocationProvider` (GPS)
    *   `SpeechRecognizer` & `TextToSpeech`
    *   Light Sensor & Vibrator

---

## 📸 Screenshots
*(Add screenshots here)*

---

## 🔧 Setup & Installation

1.  Clone the repository.
2.  Open in **Android Studio**.
3.  Add your API Keys in the local properties or code (Gemini API, Groq API).
4.  Sync Gradle and Run on an Android Device (Min SDK: Android 8.0 Oreo).

---

## 📄 License
[MIT License](LICENSE)
