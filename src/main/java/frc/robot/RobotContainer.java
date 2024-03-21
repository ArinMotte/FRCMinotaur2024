// Copyright (c) FIRST and other WPILib contributors.
// Open Source Software; you can modify and/or share it under the terms of
// the WPILib BSD license file in the root directory of this project.

/* Imported subsystem/controller classes to control parts of robot.
* Also imported camera, constants file, command files, and information 
* related to controls so robot can do thing when button pressed.
*/
package frc.robot;

import com.pathplanner.lib.auto.AutoBuilder;
import com.pathplanner.lib.auto.NamedCommands;
import com.pathplanner.lib.commands.PathPlannerAuto;
import com.pathplanner.lib.path.PathPlannerPath;

import edu.wpi.first.cameraserver.CameraServer;
import edu.wpi.first.wpilibj.GenericHID;
import edu.wpi.first.wpilibj.Joystick;
import edu.wpi.first.wpilibj2.command.Command;
import edu.wpi.first.wpilibj2.command.Commands;
import edu.wpi.first.wpilibj2.command.InstantCommand;
import edu.wpi.first.wpilibj2.command.button.JoystickButton;

// import frc.robot.subsystems.TankSubsystem;
// import frc.robot.commands.TankController;
import frc.robot.subsystems.SwerveSubsystem;
import frc.robot.subsystems.IntakeSubsystem;
import frc.robot.subsystems.ShooterSubsystem;
import frc.robot.subsystems.ClimberSubsystem;

import frc.robot.commands.ShooterController;
import frc.robot.commands.SwerveController;
import frc.robot.commands.AmpController;

import frc.robot.commands.IntakeNote;
import frc.robot.commands.IntakePull;
import frc.robot.commands.IntakePush;
import frc.robot.commands.ClimberUp;
import frc.robot.commands.ClimberDown;
import frc.robot.commands.StopIntake;
import frc.robot.commands.ScorePositionCircle;

import frc.robot.utilities.Controller;
import frc.robot.utilities.constants.Constants;


/*Below class holds all info related to controlling robot.

 * DriverController is created to drive robot.
 * Buttons are assigned so robot can change movement style related to
 * heading or score speaker or amp when told to. Swerve drive is 
 * connected to left joystick with values to help movement.
 * Shooter subsystem/controller is also created.
 * 
 * Commands are used to tell each system what to do when buttons
 * are pressed in ConfigureButtonBindings.
 * 
 * In constructor, swerve drive is told to take values from
 * joystick and robotCentric button to determine movement.
 * Camera begins taking in information, and buttons are configured.
 * 
 * Autonomous command is called (currently to move in straight line).
 */

public class RobotContainer {

  private final Joystick DriverController;
  private final Joystick OperatorController;

  private final JoystickButton resetHeading;
  private final JoystickButton robotCentric;
  private final JoystickButton findScorePosition;
  private final JoystickButton speakerScoring;
  private final JoystickButton ampScoring;
  private final JoystickButton deployIntake;
  private final JoystickButton storeIntake;
  private final JoystickButton intakeGamePiece;
  private final JoystickButton outtakeGamePiece;
  private final JoystickButton robotClimbUp;
  private final JoystickButton robotClimbDown;

  private final SwerveSubsystem swerveSubsystem;
  private final int translationAxis;
  private final int strafeAxis;
  private final int rotationAxis;

  private final ShooterSubsystem shooterSubsystem;
  private final ClimberSubsystem climberSubsystem;
  private final IntakeSubsystem intakeSubsystem;
  private final ShooterController shooterController;
  private final AmpController ampController;
  private final IntakePull pullNote;
  private final IntakePush pushNote;
  private final StopIntake stopIntake;
  private final ClimberUp climbingUp;
  private final ClimberDown climbingDown;
  private final ScorePositionCircle goScorePosition;

  public RobotContainer() {
    swerveSubsystem = new SwerveSubsystem();
    shooterSubsystem = new ShooterSubsystem();
    climberSubsystem = new ClimberSubsystem();
    intakeSubsystem = new IntakeSubsystem();

    NamedCommands.registerCommand("Speaker Shooter",  new ShooterController(shooterSubsystem, intakeSubsystem));
    NamedCommands.registerCommand("Amp Shooter",  new AmpController(shooterSubsystem, intakeSubsystem));
    NamedCommands.registerCommand("Deploy Intake", intakeSubsystem.deployIntake());
    NamedCommands.registerCommand("Spin Intake", new IntakePull(intakeSubsystem));
    NamedCommands.registerCommand("Stop Intake", new StopIntake(intakeSubsystem));
    NamedCommands.registerCommand("Store Intake", intakeSubsystem.storeIntake());
    NamedCommands.registerCommand("Intake Note", new IntakeNote(intakeSubsystem));

    DriverController = Controller.getDriverController();
    OperatorController = Controller.getOperatorController();

    resetHeading = new JoystickButton(DriverController, Constants.ControllerRawButtons.PS5Controller.Button.kSquare.value);
    robotCentric = new JoystickButton(DriverController, Constants.ControllerRawButtons.PS5Controller.Button.kCircle.value);
    findScorePosition = new JoystickButton(DriverController, Constants.ControllerRawButtons.PS5Controller.Button.kTriangle.value);
    speakerScoring = new JoystickButton(DriverController, Constants.ControllerRawButtons.PS5Controller.Button.kR1.value);
    ampScoring = new JoystickButton(DriverController, Constants.ControllerRawButtons.PS5Controller.Button.kL1.value);

    deployIntake = new JoystickButton(OperatorController, Constants.ControllerRawButtons.PS5Controller.Button.kCross.value);
    storeIntake = new JoystickButton(OperatorController, Constants.ControllerRawButtons.PS5Controller.Button.kCircle.value);
    intakeGamePiece = new JoystickButton(OperatorController, Constants.ControllerRawButtons.PS5Controller.Button.kSquare.value);
    outtakeGamePiece = new JoystickButton(OperatorController, Constants.ControllerRawButtons.PS5Controller.Button.kTriangle.value);
    robotClimbUp = new JoystickButton(OperatorController, Constants.ControllerRawButtons.PS5Controller.Button.kL1.value);
    robotClimbDown = new JoystickButton(OperatorController, Constants.ControllerRawButtons.PS5Controller.Button.kR1.value);

    translationAxis = Constants.ControllerRawButtons.PS5Controller.Axis.kLeftY.value;
    strafeAxis = Constants.ControllerRawButtons.PS5Controller.Axis.kLeftX.value;
    rotationAxis = Constants.ControllerRawButtons.PS5Controller.Axis.kRightX.value;

    shooterController = new ShooterController(shooterSubsystem, intakeSubsystem);
    ampController = new AmpController(shooterSubsystem, intakeSubsystem);
    pullNote = new IntakePull(intakeSubsystem);
    pushNote = new IntakePush(intakeSubsystem);
    stopIntake = new StopIntake(intakeSubsystem);
    climbingUp = new ClimberUp(climberSubsystem);
    climbingDown = new ClimberDown(climberSubsystem);
    goScorePosition = new ScorePositionCircle(swerveSubsystem);

    swerveSubsystem.setDefaultCommand(new SwerveController(
      swerveSubsystem, 
      () -> -DriverController.getRawAxis(translationAxis),
      () -> -DriverController.getRawAxis(strafeAxis), 
      () -> -DriverController.getRawAxis(rotationAxis), 
      () -> robotCentric.getAsBoolean())
    );
      
    configureButtonBindings();
  }

  private void configureButtonBindings() {
    resetHeading.whileTrue(new InstantCommand(() -> swerveSubsystem.resetHeading()));
    ampScoring.whileTrue(ampController);
    speakerScoring.whileTrue(shooterController);
    deployIntake.whileTrue(intakeSubsystem.deployIntake());
    storeIntake.whileTrue(intakeSubsystem.storeIntake());
    intakeGamePiece.whileTrue(pushNote);
    outtakeGamePiece.whileTrue(pullNote);
    intakeGamePiece.whileFalse(stopIntake);
    outtakeGamePiece.whileFalse(stopIntake);
    robotClimbUp.whileTrue(climbingUp);
    robotClimbDown.whileTrue(climbingDown);
    findScorePosition.whileTrue(goScorePosition);
  }

 
  public Command getAutonomousCommand() {
    PathPlannerPath path = PathPlannerPath.fromPathFile("Line");
    return AutoBuilder.followPath(path);
  }
}
