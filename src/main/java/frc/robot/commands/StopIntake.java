package frc.robot.commands;

import edu.wpi.first.wpilibj2.command.Command;
import frc.robot.subsystems.IntakeSubsystem;

public class StopIntake extends Command {
    private IntakeSubsystem intakeSubsystem;

    public StopIntake(IntakeSubsystem intakeSubsystem) {
        this.intakeSubsystem = intakeSubsystem;

        addRequirements(intakeSubsystem);
    }

    @Override
    public void execute() {
        intakeSubsystem.reset();
    }

    @Override
    public void end(boolean interrupted) {
        intakeSubsystem.reset();
    }
}
