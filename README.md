# Physics Simulator - OOPS Mini Project

A Java Swing based physics simulator made as a college mini project for Object Oriented Programming (OOP).

## PPT Quick Summary

- Three interactive modules: Solar System, Collision, and Buoyancy
- Built with Java Swing/Java2D, modular OOP design
- Real-time simulation controls and visual feedback
- Optional AI assistant via NVIDIA API and optional planet textures
- Windows-friendly run script with simple build steps

This physics simulator has a main launcher with 3 simulation modules:
- Solar System Physics Engine
- Collision Physics Engine
- Buoyancy Physics Engine

## Project Objective

The goal of this mini project is to visualize basic physics concepts in an interactive way while applying OOP principles like:
- Encapsulation
- Abstraction
- Inheritance/composition style design
- Modular class based architecture

## How the Physics Works (High Level)

- Solar System: Newtonian gravity with $F=G\frac{m_1 m_2}{r^2}$, integrated each frame to update velocity and position.
- Collisions: Momentum exchange using a coefficient of restitution, with optional gravity and wall constraints.
- Buoyancy: Archimedes' principle with drag, surface tension, currents, and wave motion for realistic behavior.

## Architecture Overview

- Launcher handles navigation between modules
- Each module is a standalone JFrame with its own simulation panel and controls
- Common UI patterns: sliders, toggles, and real-time rendering loop
- Rendering uses Java2D shapes, gradients, and animated draw cycles

## Features

### 1. Launcher
- Modern mission-select style home screen
- Opens each simulation module from one place
- Resize/maximize supported

### 2. Solar System Engine
- Orbital motion simulation
- Multiple celestial body types (planets, moons, shuttle, black hole, pulsar, wormhole, asteroids)
- Interactive controls and status panel
- Optional AI assistant (NVIDIA API)
- Optional photoreal planet textures downloaded and cached locally
- Planet close-up view with telemetry-like details

### 3. Collision Engine
- Multi-body collision simulation
- Tunable mass, velocity, elasticity/friction style controls
- Kinetic energy readout and launch vectors
- Real-time motion and collision visualization
- Optional AI assistant (NVIDIA API)

### 4. Buoyancy Engine
- Floating and sinking behavior based on density
- Fluid controls (density, viscosity, water level, waves, turbulence)
- Force arrows, flow field, and dynamic waves
- Real-time force/flow style visual feedback
- Optional AI assistant (NVIDIA API)

## Tech Stack

- Language: Java
- GUI: Java Swing + Java2D
- Build/Run: javac + java (via batch script)
- AI: NVIDIA API (OpenAI-compatible chat completions)

## Java Concepts Used

- Classes and objects for simulation entities
- Encapsulation of state (mass, velocity, forces) inside each module
- Inheritance/composition for UI panels and helpers
- Event-driven programming with listeners and timers
- Collections (lists, maps) for dynamic bodies and effects

## Folder Structure

```
oops mini project/
|-- run.bat
|-- README.md
|-- src/
|   |-- backend/
|   |   |-- SolarSystemEngine.java
|   |-- frontend/
|   |   |-- Launcher.java
|   |   |-- CollisionEngine.java
|   |   |-- BuoyancyEngine.java
|-- textures/
|   |-- cache/
```

## How to Run (Windows)

### Option 1: Using run.bat (recommended)
1. Open terminal in project root.
2. Run:

```bat
run.bat
```

This will:
- clean/create `out` folder
- compile all source files
- launch the application

### Option 2: Manual compile and run

```bat
javac -d out src\backend\*.java src\frontend\*.java
java -cp out Launcher
```

## Prerequisites

- Java JDK 8 or higher
- Windows Command Prompt or PowerShell
- Internet connection for AI assistant and texture downloads (optional)

To check Java:

```bat
java -version
javac -version
```

## Sample Use Flow

1. Start app from `run.bat`.
2. Choose module from launcher:
   - Solar System Physics Engine
   - Collisions and Buoyancy (then pick one)
3. Use sliders/toggles/buttons in control panels to observe behavior changes.

## AI Assistant (Optional)

Each module includes an embedded AI helper that calls the NVIDIA chat completions API.

Notes:
- The API key is configured in code for a smooth out-of-the-box experience.
- The default model is `meta/llama-3.1-8b-instruct`.

## Textures (Solar System Engine)

The Solar System engine can download photoreal planet textures from SolarSystemScope and cache them in `textures/cache/` for faster subsequent runs. If the textures are not available or cannot be downloaded, the app uses generated placeholders.

## Learning Outcomes

- Better understanding of simulation loops and event-driven GUI programming
- Practical usage of OOP in a real mini project
- Experience with Java Swing rendering and interactive controls

## Future Improvements

- Add save/load scenario support
- Add graphs for velocity/force over time
- Add better keyboard shortcuts and accessibility
- Add unit tests for core physics logic

## Author

- Salil Regi
- Shaheer Sheikh
- Shaurya Pandey
- Tejaswi D
- Course: Object Oriented Programming
- Semester: 2nd Sem

## License

This project is for educational/academic use.
