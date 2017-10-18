package ca.mcgill.ecse211.zipline;

import ca.mcgill.ecse211.zipline.ColorPoller.l_mode;
import ca.mcgill.ecse211.zipline.UltrasonicPoller.u_mode;

/**
 * Handles the localization of the robot.
 * 
 * @author Justin Tremblay
 *
 */
public class Localizer extends Thread {
  /* References to other classes */
  private UltrasonicLocalizer ul;
  private LightLocalizer ll;
  private ColorPoller cp;
  private UltrasonicPoller up;
  private Driver dr;

  private boolean localizing = false; // Used to block the thread.
  private boolean skip_ultrasonic = false;
  public boolean done = false;

  public enum loc_state {
    IDLE, NOT_LOCALIZED, ULTRASONIC, LIGHT, DONE
  };

  private loc_state cur_state;

  /**
   * Constructor
   * 
   * @param ul UltrasonicLocalizer, performs rising edge localization
   * @param ll LightLocalization, works alongside the ultrasonic localizer to determine the robot's
   *        position
   * @param up UltrasonicPoller, Used by the ultrasonic localizer
   * @param cp ColorPoller, Used by the light localizer
   */
  public Localizer(UltrasonicLocalizer ul, LightLocalizer ll, UltrasonicPoller up, ColorPoller cp,
      Driver dr) {
    this.ul = ul;
    this.ll = ll;
    this.up = up;
    this.cp = cp;
    this.dr = dr;
  }

  /**
   * run() method.
   */
  public void run() {
    while (true) {
      switch (cur_state) {
        case IDLE:
          cur_state = process_idle();
          break;
        case NOT_LOCALIZED:
          cur_state = process_notLocalized();
          break;
        case ULTRASONIC:
          cur_state = process_ultrasonic();
          break;
        case LIGHT:
          cur_state = process_light();
          break;
        case DONE:
          cur_state = process_done();
          break;
        default: // Should not happen.
          break;
      }
      
      /*
       * Space reserved for special cases, shouldn't be needed here.
       */
      
      try {
        Thread.sleep(20);
      } catch (Exception e) {
        // ...
      }
    }
  }

  /* State processing methods */
  
  /**
   * Checks for the value of localizing, returns IDLE if false.
   * 
   * @return new state
   */
  private loc_state process_idle() {
    return localizing ? loc_state.NOT_LOCALIZED : loc_state.IDLE;
  }

  /**
   * Checks for value of localizing again (just in case). returns IDLE if false.
   * 
   * @return new state
   */
  private loc_state process_notLocalized() {
    dr.rotate(360, true, true); // Start rotating
    
    // Fancy ternary nonsense!
    return localizing ? skip_ultrasonic ? loc_state.LIGHT : loc_state.ULTRASONIC : loc_state.IDLE;
  }

  /**
   * Sets up the ultrasonic poller and starts the ultrasonic localization
   * 
   * @return new state
   */
  private loc_state process_ultrasonic() {
    if (!localizing) {
      return loc_state.IDLE;
    }
    
    if (up.isAlive()) {
      up.setLocalizer(ul);
      up.setMode(u_mode.LOCALIZATION);
    } else {
      System.out.println("[LOCALIZER] UltrasonicPoller not running!");
      return loc_state.IDLE; // That's a big problem.
    }
    ul.start(); // Start the ultrasonic localizer.
    while (!ul.done); // Wait until the ultrasonic localization completes.
    return loc_state.LIGHT; // Go directly to light localization.
  }

  /**
   * Sets up the color poller and starts the light localization
   * 
   * @return new state
   */
  private loc_state process_light() {
    if (!localizing) {
      return loc_state.IDLE;
    }
    
    if (cp.isAlive()) {
      cp.setLocalizer(ll);
      cp.setMode(l_mode.LOCALIZATION);
    } else {
      System.out.println("[LOCALIZER] ColorPoller not running!");
      return loc_state.IDLE; // That's a big problem.
    }
    ll.start();
    while (!ll.done);
    return loc_state.DONE;
  }

  /**
   * Sets localizing to false to stop the thread from doing anything.
   * 
   * @return new state
   */
  private loc_state process_done() {
    localizing = false;
    done = true;
    
    // reset
    if (skip_ultrasonic) {
      skip_ultrasonic = false;
    }
    
    return loc_state.IDLE;
  }

  /**
   * Getter for the state
   * 
   * @return current state of the localizer
   */
  public synchronized loc_state getCurrentState() {
    return cur_state;
  }

  /**
   * Starts the localization process.
   * 
   * @param skip_ultrasonic set to true s to skip ultrasonic localization.
   */
  public synchronized void startLocalization(boolean skip_ultrasonic) {
    this.skip_ultrasonic = skip_ultrasonic;
    localizing = true;
  }
  
  /**
   * probably won't ever be usefull.
   */
  public synchronized void abortLocalization() {
    localizing = false;
  }
}