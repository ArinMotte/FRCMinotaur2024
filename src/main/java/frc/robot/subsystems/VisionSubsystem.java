package frc.robot.subsystems;

import edu.wpi.first.apriltag.AprilTagFieldLayout;
import edu.wpi.first.apriltag.AprilTagFields;
import edu.wpi.first.math.geometry.Pose2d;
import edu.wpi.first.math.geometry.Pose3d;
import edu.wpi.first.math.geometry.Rotation2d;
import edu.wpi.first.math.geometry.Transform2d;
import edu.wpi.first.math.geometry.Transform3d;
import edu.wpi.first.math.geometry.Translation2d;
import edu.wpi.first.networktables.NetworkTableInstance;
import edu.wpi.first.wpilibj.Timer;
import edu.wpi.first.wpilibj.shuffleboard.Shuffleboard;
import edu.wpi.first.wpilibj.shuffleboard.ShuffleboardTab;
import edu.wpi.first.wpilibj2.command.Command;
import edu.wpi.first.wpilibj2.command.SubsystemBase;
import frc.robot.utilities.LimelightHelpers;
import frc.robot.utilities.PoseEstimator;
import frc.robot.utilities.LimelightHelpers.LimelightTarget_Fiducial;
import frc.robot.utilities.constants.Constants;

import java.text.DecimalFormat;
import org.photonvision.PhotonCamera;
import org.photonvision.PhotonUtils;
import org.photonvision.targeting.PhotonTrackedTarget;

public class VisionSubsystem extends SubsystemBase {
  private Pose2d botPose;
  private Pose2d estimatePose;
  private double limeLatency;
  private boolean apriltagLimelightConnected = false;
  private boolean NNLimelightConnected = false;

  private double photonTimestamp;
  private PhotonCamera photonCam_1;
  private boolean photon1HasTargets;
  private AprilTagFieldLayout aprilTagFieldLayout;

  // For Note detection in the future
  private double detectHorizontalOffset = 0;
  private double detectVerticalOffset = 0;

  private boolean detectTarget = false;
  private LimelightHelpers.LimelightResults jsonResults, detectJsonResults;
  private Pose2d targetRobotRelativePose;
  private Pose2d noteFieldRelativePose;
  private Pose2d noteRobotRelativePose;
  private ShuffleboardTab tab = Shuffleboard.getTab("Driver Cam");

  // testing
  private final DecimalFormat df = new DecimalFormat();

  private static VisionSubsystem instance;

  public static VisionSubsystem getInstance() {
    if (instance == null) instance = new VisionSubsystem();
    return instance;
  }

  // TODO - see if adding setCameraPose_RobotSpace() is needed from LimelightHelpers
  private VisionSubsystem() {
    setName("Vision");
    botPose = new Pose2d();
    estimatePose = new Pose2d();
    noteFieldRelativePose = new Pose2d();
    noteRobotRelativePose = new Pose2d();
    noteRobotRelativePose = new Pose2d();
    targetRobotRelativePose = new Pose2d();
    photonTimestamp = 0.0;
    limeLatency = 0.0;

    // Changes vision mode between limelight and photonvision for easy switching
    if (Constants.VisionConstants.IS_LIMELIGHT_MODE) {
      // configure both limelights
      LimelightHelpers.setLEDMode_ForceOn(Constants.VisionConstants.POSE_LIMELIGHT);
      setLimelightPipeline(Constants.VisionConstants.POSE_LIMELIGHT, Constants.VisionConstants.APRILTAG_PIPELINE);
      LimelightHelpers.setCameraPose_RobotSpace(
          Constants.VisionConstants.POSE_LIMELIGHT,
          Constants.VisionConstants.POSE_LIME_X,
          Constants.VisionConstants.POSE_LIME_Y,
          Constants.VisionConstants.POSE_LIME_Z,
          Constants.VisionConstants.POSE_LIME_ROLL,
          Constants.VisionConstants.POSE_LIME_PITCH,
          Constants.VisionConstants.POSE_LIME_YAW);
    }

    if (Constants.VisionConstants.IS_NEURAL_NET) {
        LimelightHelpers.setLEDMode_ForceOff(Constants.VisionConstants.NN_LIMELIGHT);
        setLimelightPipeline(Constants.VisionConstants.NN_LIMELIGHT, Constants.VisionConstants.NOTE_DETECTOR_PIPELINE);
      }

    if (Constants.VisionConstants.IS_PHOTON_VISION_MODE) { // Configure photonvision camera
      photonCam_1 = new PhotonCamera(Constants.VisionConstants.POSE_PHOTON_1);
      //photonCam_2 = new PhotonCamera(Constants.VisionConstants.POSE_PHOTON_2);
      photon1HasTargets = false;
      try {
        aprilTagFieldLayout =
            AprilTagFieldLayout.loadFromResource(AprilTagFields.k2024Crescendo.m_resourceFile);
      } catch (Exception e) {
        System.out.println("Field layout not found");
      }
    }

    if (Constants.VisionConstants.DRIVER_CAMERA_ACTIVE){
      tab.addCamera("Driver Camera", "Drive cam", Constants.VisionConstants.DRIVER_CAM_STREAM);
    }

    // printing purposes
    df.setMaximumFractionDigits(2);
  }


@Override
  public void periodic() {
    /*Ensures empty json not fed to pipeline*/
    apriltagLimelightConnected =
        !NetworkTableInstance.getDefault()
            .getTable(Constants.VisionConstants.POSE_LIMELIGHT)
            .getEntry("json")
            .getString("")
            .equals("");

    NNLimelightConnected =
        !NetworkTableInstance.getDefault()
            .getTable(Constants.VisionConstants.NN_LIMELIGHT)
            .getEntry("json")
            .getString("")
            .equals("");
      
    if (Constants.VisionConstants.IS_LIMELIGHT_MODE && apriltagLimelightConnected) {
      jsonResults = LimelightHelpers.getLatestResults(Constants.VisionConstants.POSE_LIMELIGHT);

      estimatePose = LimelightHelpers.getBotPose2d_wpiBlue(Constants.VisionConstants.POSE_LIMELIGHT);

      if (visionAccurate(estimatePose)) {
        // Blue alliance means origin is bottom right of the field 
        limeLatency =
            LimelightHelpers.getLatency_Pipeline(Constants.VisionConstants.POSE_LIMELIGHT)
                + LimelightHelpers.getLatency_Capture(Constants.VisionConstants.POSE_LIMELIGHT);
        botPose = estimatePose;
      }
    }

    // Photonvision Result
    // The documentation for this is here:
    // https://docs.photonvision.org/en/latest/docs/programming/photonlib/robot-pose-estimator.html
    // The example code was missing, and we came up with this: 
    // NOTE - PHOTONVISON GIVES POSES WITH BLUE ALLIANCE AS THE ORIGN ALWAYS!!!
    if (Constants.VisionConstants.IS_PHOTON_VISION_MODE) {
      var result_1 = photonCam_1.getLatestResult();
      photon1HasTargets = result_1.hasTargets();

      if (result_1.getMultiTagResult().estimatedPose.isPresent){
        photonTimestamp = result_1.getTimestampSeconds();
        Transform3d fieldToCamera = result_1.getMultiTagResult().estimatedPose.best;
        Transform3d fieldCamToRobot = fieldToCamera.plus(Constants.VisionConstants.PHOTON_1_CAM_TO_ROBOT);
        botPose = new Pose2d(fieldCamToRobot.getX(), fieldCamToRobot.getY(), new Rotation2d(fieldCamToRobot.getRotation().getZ()));
      }
      else if (photon1HasTargets) {
        PhotonTrackedTarget target = result_1.getBestTarget();
        if (target.getPoseAmbiguity() < Constants.VisionConstants.POSE_AMBIGUITY_CUTOFF){
          photonTimestamp = result_1.getTimestampSeconds();

          Transform3d bestCameraToTarget = target.getBestCameraToTarget();
          Pose3d tagPose = aprilTagFieldLayout.getTagPose(target.getFiducialId()).get();
          Pose3d currentPose3d = PhotonUtils.estimateFieldToRobotAprilTag(bestCameraToTarget, tagPose, Constants.VisionConstants.PHOTON_1_CAM_TO_ROBOT);
          botPose = currentPose3d.toPose2d();
        }
      }
      else{
        photon1HasTargets = false;
      }
    }

    //Does math to see where the note is
    if (Constants.VisionConstants.IS_NEURAL_NET && NNLimelightConnected) {
      detectTarget = LimelightHelpers.getTV(Constants.VisionConstants.NN_LIMELIGHT);
      detectJsonResults = LimelightHelpers.getLatestResults(Constants.VisionConstants.NN_LIMELIGHT);
      //var rrResults = detectJsonResults.targetingResults.targets_Retro[0];

      if (detectTarget) {
        detectHorizontalOffset = -LimelightHelpers.getTX(Constants.VisionConstants.NN_LIMELIGHT); //HAD TO NEGATIVE TO MAKE CCW POSITIVE
        detectVerticalOffset = LimelightHelpers.getTY(Constants.VisionConstants.NN_LIMELIGHT);
        double targetDist = targetDistanceMetersCamera(Constants.VisionConstants.NN_LIME_Z, Constants.VisionConstants.NN_LIME_PITCH, 0, detectVerticalOffset);
        //Note: limelight is already CCW positive, so tx does not have to be * -1
        Translation2d camToTargTrans = estimateCameraToTargetTranslation(targetDist, detectHorizontalOffset);

        //Code for robot relative note tracking
        Transform2d robotToNoteTransform = Constants.VisionConstants.NN_ROBOT_TO_LIME_2D.plus(new Transform2d(camToTargTrans, Rotation2d.fromDegrees(0.0)));
        Rotation2d targetAngleRobotRelative = robotToNoteTransform.getTranslation().getAngle().plus(new Rotation2d(Math.PI));
        noteRobotRelativePose = new Pose2d(robotToNoteTransform.getTranslation(), targetAngleRobotRelative);

        //Code for field relative note tracking
        Pose2d currentBotPoseFieldRelative = PoseEstimator.getInstance().getPosition();

        Pose2d camPoseFieldRelative = currentBotPoseFieldRelative.plus(Constants.VisionConstants.NN_ROBOT_TO_LIME_2D);
        noteFieldRelativePose = camPoseFieldRelative.plus(new Transform2d(camToTargTrans, Rotation2d.fromDegrees(0.0)));
        Translation2d currentBotTranslation = currentBotPoseFieldRelative.getTranslation();
        Translation2d targetVector = currentBotTranslation.minus(noteFieldRelativePose.getTranslation());
        Rotation2d targetAngle = targetVector.getAngle();
        
        noteFieldRelativePose = new Pose2d(noteFieldRelativePose.getTranslation(), targetAngle);
      }
    }

  }

  /**
   * @return Pose2d location of note Field Relative
   */
  public Pose2d getNotePose2d(){
    return noteFieldRelativePose;
  }

  /**
   * @return Pose2d location of note Field Relative
   */
  public Pose2d getRobotRelativeNotePose2d(){
    return noteRobotRelativePose;
  }


  /**
   * @return Timestamp of photonvision's latest reading
   */
  public double getPhotonTimestamp() {
    return photonTimestamp;
  }

  /**
   * @return boolean if photonvision has targets
   */
  public boolean photonHasTargets() {
    return photon1HasTargets;
  }
  
  /**
   * @return RobotPose2d with the apriltag as the origin (for chase apriltag command)
   */
  public Pose2d getRobotPose2d_TargetSpace() {
    return LimelightHelpers.getBotPose2d_TargetSpace(Constants.VisionConstants.POSE_LIMELIGHT);
  }

  /**
   * @return Pose2d of the apriltag with the robot as the origin
   */
  public Pose2d getTargetRobotPose_RobotSpace() {
    return LimelightHelpers.getTargetPose2d_RobotSpace(Constants.VisionConstants.POSE_LIMELIGHT);
  }

  // APRILTAG HELPER METHODS

  /**
   * @return if vision should be trusted more than estimated pose
   */
  public boolean visionAccurate(Pose2d currentPose) {
    return isValidPose() && (isInMap(currentPose) || multipleTargetsInView());
  }

  /**
   * @return whether or not vision sees a tag
   */
  public boolean isValidPose() {
    /* Disregard Vision if there are no targets in view */
    if (Constants.VisionConstants.IS_LIMELIGHT_MODE) {
      return LimelightHelpers.getTV(Constants.VisionConstants.POSE_LIMELIGHT);
    }
    if (Constants.VisionConstants.IS_PHOTON_VISION_MODE) {
      return photonHasTargets();
    }
    return false;
  }

  // This is a suss function - need to test it
  public boolean isInMap(Pose2d currentPose) {
    return ((currentPose.getX() >= 0.0 && currentPose.getX() <= Constants.VisionConstants.FIELD_LENGTH_METERS)
        && (currentPose.getY() >= 0.0 && currentPose.getY() <= Constants.VisionConstants.FIELD_WIDTH_METERS));
  }

  /**
   * @return whether the camera sees multiple tags or not
   */
  public boolean multipleTargetsInView() {
    if (jsonResults == null) {
      return false;
    }
    LimelightTarget_Fiducial[] tags = jsonResults.targetingResults.targets_Fiducials;
    if (tags.length > 1) {
      return true;
    }
    return false;
  }

  // Getter for visionBotPose - NEED TO DO TESTING TO MAKE SURE NO NULL ERRORS

  public Pose2d visionBotPose() {
    return botPose;
  }

  /**
   * @return the total latency of the limelight camera
   */
  public double getTotalLatency() {
    return limeLatency;
  }

  /**
   * Gets the camera capture time in seconds. Only used for limelight
   *
   * @param latencyMillis the latency of the camera in milliseconds
   * @return the camera capture time in seconds
   */
  public double getTimestampSeconds(double latencyMillis) {
    return Timer.getFPGATimestamp() - (latencyMillis / 1000d);
  }

  /**
   * @param limelight name of limelight to control in {@link Constants.VisionConstants}
   * @param pipelineIndex use pipeline indexes in {@link Constants.VisionConstants}
   */
  public void setLimelightPipeline(String limelight, int pipelineIndex) {
    LimelightHelpers.setPipelineIndex(limelight, pipelineIndex);
  }

  /**
   * Gets target distance from the camera
   * @param cameraHeight distance from lens to floor of camera in meters
   * @param cameraAngle pitch of camera in radians
   * @param targetHeight distance from floor to center of target in meters
   * @param targetOffsetAngle_Vertical ty entry from limelight of target crosshair (in degrees)
   * @return the distance to the target in meters
   */
  public double targetDistanceMetersCamera(
      double cameraHeight,
      double cameraAngle,
      double targetHeight,
      double targetOffsetAngle_Vertical) {
    double angleToGoalRadians = cameraAngle + targetOffsetAngle_Vertical * (3.14159 / 180.0);
    return (targetHeight - cameraHeight) / Math.tan(angleToGoalRadians);
  }

   /**
   * @param targetDistanceMeters component of distance from camera to target
   * @param targetOffsetAngle_Horizontal tx entry from limelight of target crosshair (in degrees)
   * @return the translation to the target in meters
   */
  public Translation2d estimateCameraToTargetTranslation(double targetDistanceMeters, double targetOffsetAngle_Horizontal){
    Rotation2d yaw = Rotation2d.fromDegrees(targetOffsetAngle_Horizontal);
    return new Translation2d(
      yaw.getCos() * (targetDistanceMeters), yaw.getSin() * targetDistanceMeters);
  }
/**
   * @param cameraToTargetTranslation2d the translation from estimate camera to target
   * @param targetOffsetAngle_Horizontal tx entry from limelight of target crosshair (in degrees)
   * @return the position of the target in terms of the camera
   */
  public Pose2d estimateCameraToTargetPose2d(Translation2d cameraToTargetTranslation2d, double targetOffsetAngle_Horizontal){
    return new Pose2d(cameraToTargetTranslation2d, Rotation2d.fromDegrees(targetOffsetAngle_Horizontal));
  }


/**
   * @param camToTargetPose the camera to target pose 2d
   * @param camToRobot the transform from the x and y of the camera to the center of the robot
   * @return the position of the target relative to the robot
   */
  public Pose2d camPoseToRobotRelativeTargetPose2d(Pose2d camToTargetPose, Transform2d camToRobot){
    return camToTargetPose.transformBy(camToRobot);
    
  }

  /**
   * RobotRelativePose of the current target
   * @return the position of the target relative to the robot
   */
  public Pose2d targetPoseRobotSpace(){
    return targetRobotRelativePose;
  }

  /**
   * @param notePoseRobotRelative the RobotRelative Pose2d of the note
   * @param botPoseFieldRelative The FieldRelative Pose2d of the robot
   * @return the FieldRelative Pose2d of the note
   */
  public Pose2d notePoseFieldSpace(Pose2d notePoseRobotRelative, Pose2d botPoseFieldRelative){
    Transform2d noteTransform = new Transform2d(notePoseRobotRelative.getTranslation(), notePoseRobotRelative.getRotation());
    Pose2d notePose = botPoseFieldRelative.transformBy(noteTransform);
    return notePose; 
  }

  /**
   * Commnad to go to the note
   * @return a follow path command to drive to the note
   */
  public Command onTheFlyToNoteCommand(){
    return new SwerveSubsystem().onTheFlyPathCommand(this::getNotePose2d); //doing this::getNotePose2d converts to a supplier
  }

  /**
   * Commnad to go to in front of note using PID
   * @return a PID command to drive in front of a note
   */
  public Command PID_thenOnTheFlyToNoteCommand(){
    return new SwerveSubsystem().chaseThenOnTheFlyCommand(this::getNotePose2d);
  }

  /**
   * Commnad to go to in front of note using PID and then drive to the note
   * @return a PID and then on-the-fly command to drive onto a note
   */
  public Command PIDtoNoteCommand(){
    return new SwerveSubsystem().chasePoseCommand(this::getNotePose2d);
  }

  /**
   * Command that uses PID to drive to the note robot relative
   * @return a PID command to drive onto a note robot relative
   */
  public Command PIDtoNoteRobotRelativeCommand(){
    return new SwerveSubsystem().chasePoseRobotRelativeCommand(this::getRobotRelativeNotePose2d);
  }
}