# Physics Simulator - OOPS Mini Project

A Java Swing based physics simulator made as a college mini project for Object Oriented Programming (OOP).

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

## Features

### 1. Launcher
- Modern mission-select style home screen
- Opens each simulation module from one place
- Resize/maximize supported

### 2. Solar System Engine
- Orbital motion simulation
- Multiple celestial body types (planets, moons, shuttle, black hole, pulsar, wormhole, asteroids)
- Interactive controls and status panel

### 3. Collision Engine
- Multi-body collision simulation
- Tunable mass, velocity, elasticity/friction style controls
- Real-time motion and collision visualization

### 4. Buoyancy Engine
- Floating and sinking behavior based on density
- Fluid controls (density, viscosity, water level, waves, turbulence)
- Real-time force/flow style visual feedback

## Tech Stack

- Language: Java
- GUI: Java Swing + Java2D
- Build/Run: javac + java (via batch script)

## Folder Structure

```
OOPS Mini Project 2nd sem/
|-- run.bat
|-- README.md
|-- src/
|   |-- frontend/
|   |   |-- Launcher.java
|   |-- backend/
|   |   |-- SolarSystemEngine.java
|   |   |-- CollisionEngine.java
|   |   |-- BuoyancyEngine.java
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
