// Copyright (c) FIRST and other WPILib contributors.
// Open Source Software; you can modify and/or share it under the terms of
// the WPILib BSD license file in the root directory of this project.

package frc.robot;

import static edu.wpi.first.units.Units.*;

import java.lang.reflect.Modifier;

import com.ctre.phoenix6.swerve.SwerveModule.DriveRequestType;
import com.ctre.phoenix6.swerve.SwerveRequest;

import com.pathplanner.lib.auto.AutoBuilder;
import com.pathplanner.lib.auto.NamedCommands;

import edu.wpi.first.math.geometry.Rotation2d;
import edu.wpi.first.wpilibj.smartdashboard.SendableChooser;
import edu.wpi.first.wpilibj.smartdashboard.SmartDashboard;
import edu.wpi.first.wpilibj2.command.Command;
import edu.wpi.first.wpilibj2.command.Commands;
import edu.wpi.first.wpilibj2.command.InstantCommand;
import edu.wpi.first.wpilibj2.command.button.CommandGenericHID;
import edu.wpi.first.wpilibj2.command.button.CommandXboxController;
import edu.wpi.first.wpilibj2.command.sysid.SysIdRoutine.Direction;
import frc.robot.Constants.Elevator.ElevatorPosition;
import frc.robot.generated.TunerConstants;
import frc.robot.subsystems.Climber;
import frc.robot.subsystems.CommandSwerveDrivetrain;
import frc.robot.subsystems.Elevator;
import frc.robot.subsystems.Intake;

public class RobotContainer {
    private double MaxSpeed = TunerConstants.kSpeedAt12Volts.in(MetersPerSecond); // kSpeedAt12Volts desired top speed
    private double MaxAngularRate = RotationsPerSecond.of(0.75).in(RadiansPerSecond); // 3/4 of a rotation per second max angular velocity
    public double driveSpeedLimiter = 1.0d;

    /* Setting up bindings for necessary control of the swerve drive platform */
    private final SwerveRequest.FieldCentric drive = new SwerveRequest.FieldCentric()
            .withDeadband(MaxSpeed * 0.1).withRotationalDeadband(MaxAngularRate * 0.1) // Add a 10% deadband
            .withDriveRequestType(DriveRequestType.OpenLoopVoltage); // Use open-loop control for drive motors
    private final SwerveRequest.SwerveDriveBrake brake = new SwerveRequest.SwerveDriveBrake();
    private final SwerveRequest.PointWheelsAt point = new SwerveRequest.PointWheelsAt();
    private final SwerveRequest.RobotCentric forwardStraight = new SwerveRequest.RobotCentric()
            .withDriveRequestType(DriveRequestType.OpenLoopVoltage);

// Brian-disabled logging so that it did not fill up storage 3/2/25    
private final Telemetry logger = new Telemetry(MaxSpeed);
//SignalLogger.stop(); // Can this even go here?
    private final CommandXboxController joystick = new CommandXboxController(0);
    private final CommandXboxController operator = new CommandXboxController(1);

    public final CommandSwerveDrivetrain drivetrain = TunerConstants.createDrivetrain();
    public final Intake intake = new Intake();
    public final Elevator elevator = new Elevator();
    public final Climber climber =  new Climber();
    //public final Climber climber = new Climber();

    /* Path follower */
    private final SendableChooser<Command> autoChooser;

    public RobotContainer() {

        NamedCommands.registerCommand("autointake", intake.intakeAlgae());
        NamedCommands.registerCommand("autoelevator", (elevator.moveToPositionCommand(() -> ElevatorPosition.L1)));

        autoChooser = AutoBuilder.buildAutoChooser("Tests");
        SmartDashboard.putData("Auto Mode", autoChooser);
        configureBindings();
    }

    private void configureBindings() {
        // Note that X is defined as forward according to WPILib convention,
        // and Y is defined as to the left according to WPILib convention.
        drivetrain.setDefaultCommand(
            // Drivetrain will execute this command periodically
            drivetrain.applyRequest(() ->
                drive.withVelocityX(-joystick.getLeftY() * MaxSpeed  * driveSpeedLimiter) // Drive forward with negative Y (forward)
                    .withVelocityY(-joystick.getLeftX() * MaxSpeed * driveSpeedLimiter) // Drive left with negative X (left)
                    .withRotationalRate(-joystick.getRightX() * MaxAngularRate) // Drive counterclockwise with negative X (left)
            )
        );

        // Run SysId routines when holding back/start and X/Y.
        // Note that each routine should be run exactly once in a single log.
        joystick.back().and(joystick.y()).whileTrue(drivetrain.sysIdDynamic(Direction.kForward));
        joystick.back().and(joystick.x()).whileTrue(drivetrain.sysIdDynamic(Direction.kReverse));
        joystick.start().and(joystick.y()).whileTrue(drivetrain.sysIdQuasistatic(Direction.kForward));
        joystick.start().and(joystick.x()).whileTrue(drivetrain.sysIdQuasistatic(Direction.kReverse));
        joystick.rightTrigger().whileTrue(intake.intakeAlgae());
        joystick.leftTrigger().whileTrue(intake.shootAlgae());
        joystick.leftBumper().whileTrue(Commands.startEnd( () -> driveSpeedLimiter = 0.2, () -> driveSpeedLimiter = 1.0));
        joystick.pov(0).whileTrue(climber.winchDownCommand());
        joystick.pov(180).whileTrue(climber.winchUpCommand());
        joystick.x().and(joystick.a()).onTrue(drivetrain.runOnce(() -> drivetrain.seedFieldCentric()));

        operator.a().onTrue(elevator.moveToPositionCommand(() -> ElevatorPosition.BOTTOM)
            .andThen(() -> driveSpeedLimiter = 1.0));
            //.andThen(intake.intakeAlgae()));
        operator.b().onTrue(elevator.moveToPositionCommand(() -> ElevatorPosition.PROCESSOR));
        operator.x().onTrue(elevator.moveToPositionCommand(() -> ElevatorPosition.L1));
        operator.y().onTrue(elevator.moveToPositionCommand(() -> ElevatorPosition.L2));
        operator.leftBumper().onTrue(elevator.moveToPositionCommand(() -> ElevatorPosition.L3));
        operator.rightBumper().onTrue(elevator.moveToPositionCommand(() -> ElevatorPosition.L4));
        operator.pov(180).onTrue(elevator.moveToPositionCommand(() -> ElevatorPosition.ALGAE));
        operator.rightTrigger().whileTrue(intake.intakeAlgae());
        operator.leftTrigger().whileTrue(intake.shootAlgae());
        operator.pov(90).whileTrue(intake.shootAlgaeTop());
        drivetrain.registerTelemetry(logger::telemeterize);
    }


    public Command getAutonomousCommand() {
        /* Run the path selected from the auto chooser */
        return autoChooser.getSelected();
    }
}
