# Mjolnir Template Dropper for FTC
<!-- this is used to build the plugin, don't remove these comments -->
<!-- Plugin description -->
A toolset for FTC software development, with a variety of actively updated utilities. Made by Team [**5273 ARC Thunder**](https://www.andoverrobotics.com/teams/arc-thunder)
## Tools
### Easy **Opmode Creation**
- Support for **Java** & **Kotlin**
- Selector for **LinearOpMode** vs **Iterative (Regular) Opmode**
- Selector for **Autonomous** vs **TeleOp**
Right click inside the folder where you want to create an Opmode -> New -> FTC Opmode (Under "File")
![Demo gif](https://github.com/user-attachments/assets/63721ec3-4e31-4f57-bb91-496abb123d77)
### Easy Library Imports
- One click to add the Gradle dependencies and GitHub files for several libraries
Current Support:
- Roadrunner 1.0
- PedroPathing
- FTCLib
- NextFTC
Right Click on your TeamCode folder and click "Add Library", then select the checkboxes of the libraries you'd like and hit "Ok"
### Hardware Creation
- Add common base FTC hardware types (Ex. DCMotor, Servo) to a class with just a few clicks
Right Click in the code editor, click "Add FTC Hardware", and fill in information about the hardware you'd like to add with the checkboxes and fields
### FSM Creation
- Create an [FSM](https://gm0.org/en/latest/docs/software/concepts/finite-state-machines.html) (Finite State Machine) with an enum and switch using just a few buttons
1. Right click in the code editor, click on "Add FSM", and fill in the name and fields of your different states
2. Add a state variable to your class
3. Right click and click "Use FSM" to create a switch statement with all your states
## Planned Features
**Autofilling Constructors**
- Automatic hardware map instantiation with class names
- Potentially using XML HardwareMap for autofilling

System to contribute **Open Source Templates directly through Android Studio**
- Teams get easy outreach!
- No fiddling around with github required - all in IDE
- Focus particularly on templates for specific libraries :

### Please suggest new features or changes to existing ones through our reviews or at [our email](arnav@andoverrobotics.com)
<!-- Plugin description end -->
