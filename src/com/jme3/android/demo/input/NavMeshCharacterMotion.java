package com.jme3.android.demo.input;

import com.jme3.ai.navmesh.DebugInfo;
import com.jme3.ai.navmesh.NavMesh;
import com.jme3.ai.navmesh.NavMeshPathfinder;
import com.jme3.ai.navmesh.Path;
import com.jme3.android.demo.Main;
import com.jme3.android.demo.system.CharacterHandler;
import com.jme3.android.demo.utils.GeometryUtils;
import com.jme3.app.Application;
import com.jme3.app.state.AbstractAppState;
import com.jme3.app.state.AppStateManager;
import com.jme3.asset.AssetManager;
import com.jme3.bullet.control.BetterCharacterControl;
import com.jme3.collision.CollisionResult;
import com.jme3.collision.CollisionResults;
import com.jme3.input.event.TouchEvent;
import com.jme3.math.Ray;
import com.jme3.math.Vector2f;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.system.AppSettings;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author iwgeric
 */
public class NavMeshCharacterMotion extends AbstractAppState implements
        InputActionListener, CharacterMotion {
    private static final Logger logger = Logger.getLogger(DpadCharacterMotion.class.getName());

    private AppStateManager stateManager = null;
    private Main app = null;
    private AssetManager assetManager = null;
    private Node guiNode = null;
    private Node rootNode = null;
    private AppSettings appSettings = null;
    private Camera camera = null;

    private Integer pointerId = null;

    private CharacterHandler characterHandler = null;
    private BetterCharacterControl characterControl = null;
    private Spatial characterSpatial = null;
    private Vector3f maxVelocity = new Vector3f(1f, 0f, 1f);
    private Vector3f walkDirection = new Vector3f();

    private Geometry geoNavMesh;
    private NavMesh navMesh;
    private NavMeshPathfinder navMeshPathFinder;
    private DebugInfo navMeshDebugInfo = new DebugInfo();
    private Node navWayPointsNode = new Node("navWayPointsNode");

    private Vector3f tmpTarget = new Vector3f(-12.3219385f, 0.49764287f, 30.71899f);

    public NavMeshCharacterMotion() {
        super();
    }

    public void setCharacterHandler(CharacterHandler characterHandler) {
        this.characterHandler = characterHandler;
        this.characterControl = characterHandler.getCharPhysicsControl();
        this.characterSpatial = characterHandler.getModel();
    }

    private void computeNav(Vector3f startLocation, Vector3f targetLocation) {
        logger.log(Level.INFO, "Starting Path Finder");
        logger.log(Level.INFO, "Starting Position: {0}, Target Position: {1}",
                new Object[]{startLocation, targetLocation});

        navMeshPathFinder.setPosition(startLocation);
        navMeshPathFinder.computePath(targetLocation, navMeshDebugInfo);

        logger.log(Level.INFO, "Finish Path Finder");
    }

    @Override
    public void initialize(AppStateManager stateManager, Application app) {
        this.stateManager = stateManager;
        this.app = (Main)app;
        this.assetManager = app.getAssetManager();
        this.guiNode = this.app.getGuiNode();
        this.rootNode = this.app.getRootNode();
        this.appSettings = app.getContext().getSettings();
        this.camera = this.app.getCamera();

        setCharacterHandler(this.app.getSceneAppState().getMainCharacter());

        Geometry navMeshSpatial = this.app.getSceneAppState().getNavMesh();

        logger.log(Level.INFO, "Creating NavMesh and PathFinder");
        navMesh = new NavMesh(navMeshSpatial.getMesh());
        navMeshPathFinder = new NavMeshPathfinder(navMesh);
        navMeshDebugInfo = new DebugInfo();
        this.rootNode.attachChild(navWayPointsNode);

        super.initialize(stateManager, app);
    }

    @Override
    public void setEnabled(boolean enabled) {
        if (enabled) {
        } else {
        }
        pointerId = null;
        super.setEnabled(enabled);
    }

    @Override
    public void update(float tpf) {
        super.update(tpf);

        if (characterHandler.getCharacterMotion() == null || !characterHandler.getCharacterMotion().equals(this)) {
            return;
        }

        //getNextWayPoint will return always the same waypoint until we manually advance to the next
        Path.Waypoint wayPoint = navMeshPathFinder.getNextWaypoint();
        if(wayPoint != null) {
            float distance = navMeshPathFinder.getDistanceToWaypoint();
            Vector3f vector = wayPoint.getPosition().subtract(characterSpatial.getWorldTranslation());
//            logger.log(Level.INFO, "Distance: {0}, vector: {1}",
//                    new Object[]{distance, vector});
            if(!(vector.length() < 0.5)){
                //move the spatial to location while its not there
                walkDirection.set(vector.normalize());
                walkDirection.multLocal(maxVelocity);
            } else{
                //if we are at the waypoint already, go to the next one
                if (navMeshPathFinder.isAtGoalWaypoint()) {
//                    logger.log(Level.INFO, "Reached Goal Waypoint");
                    walkDirection.set(Vector3f.ZERO);
                    characterHandler.setCharacterMotion(null);
                    navWayPointsNode.detachAllChildren();
                } else {
                    navMeshPathFinder.goToNextWaypoint(navMeshDebugInfo);
                    GeometryUtils.createLocationTriad("WayPoint", navMeshPathFinder.getWaypointPosition(), navWayPointsNode, 2f, 3f, assetManager);
//                    logger.log(Level.INFO, "Switching to next Waypoint");
                }
            }
        } else {
//            logger.log(Level.INFO, "WayPoint is null");
            walkDirection.set(Vector3f.ZERO);
        }

//        logger.log(Level.INFO, "WalkDirection: {0}", walkDirection);
        characterControl.setWalkDirection(walkDirection);
        if (walkDirection.lengthSquared() > 0) {
            logger.log(Level.INFO, "Setting WalkDirection: {0}", walkDirection);
            characterControl.setViewDirection(walkDirection);
        }
    }

    @Override
    public void cleanup() {
        super.cleanup();
    }

    public boolean onInputAction(TouchEvent event, float tpf) {
        boolean consumed = false;

        if (!isEnabled()) {
            return false;
        }
        if (characterHandler.getCharacterMotion() != null && !characterHandler.getCharacterMotion().equals(this)) {
            logger.log(Level.INFO, "characterMotion: {0}", characterHandler.getCharacterMotion());
            return false;
        }

        switch (event.getType()) {
            case TAP:
                Vector3f target = getTargetLocation(
                        app.getSceneAppState().getWorldNode(), app.getSceneAppState().getGroundNode(),
                        event.getX(), event.getY());
                if (target != null) {
                    characterHandler.setCharacterMotion(this);
                    computeNav(characterSpatial.getWorldTranslation(), target);
                    consumed = true;

                }
                break;
        }
        return consumed;
    }

    private Vector3f getTargetLocation(Spatial allObjects, Spatial targetObjects, float x, float y) {
        CollisionResults results = new CollisionResults();
        Vector2f click2d = new Vector2f(x, y);
        Vector3f click3d = camera.getWorldCoordinates(
                new Vector2f(click2d.x, click2d.y), 0f).clone();
        Vector3f dir = camera.getWorldCoordinates(
                new Vector2f(click2d.x, click2d.y), 1f).subtractLocal(click3d).normalizeLocal();
        Ray ray = new Ray(click3d, dir);
        allObjects.collideWith(ray, results);

        if (results.size() > 0) {
            for (CollisionResult result: results) {
                logger.log(Level.INFO, "distance: {0}, name: {1}",
                        new Object[]{result.getDistance(), result.getGeometry().getName()});
            }
            CollisionResult closestCollision = results.getClosestCollision();
            if (targetObjects instanceof Node) {
                if (((Node)targetObjects).hasChild(closestCollision.getGeometry())) {
                    return closestCollision.getContactPoint();
                }
            } else {
                if (targetObjects.equals(closestCollision.getGeometry())) {
                    return closestCollision.getContactPoint();
                }
            }

        }

        return null;
    }

}