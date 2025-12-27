package NEW;

import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;
import javafx.scene.image.Image;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import javafx.scene.media.AudioClip;
import java.io.File;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;

/**
 * Main class of the ThunderFighter game, inheriting from JavaFX Application class
 * Implements an object-oriented graphical shooting game with JavaFX, following OOP principles (encapsulation, inheritance, polymorphism)
 * Core features: Player control, enemy/BOSS spawning, bullet system, buff mechanics, collision detection, sound effects, and visual effects
 */
public class ThunderFighter extends Application {
    // Game window constants - fixed resolution for consistent display
    private static final int WIDTH = 480;       // Game window width (pixels)
    private static final int HEIGHT = 800;      // Game window height (pixels)
    private static final int DEAD_LINE = HEIGHT - 120; // Deadline line: Enemies crossing this trigger game over
    private static final int TOP_CLEANUP_LINE = 50;   // Top cleanup line: Bullets above this are removed to save memory
    private static final int BOSS_TRIGGER_SCORE = 3000; // Score required to spawn the final BOSS

    // Player-related variables
    private double cannonX = WIDTH / 2.0; // X-coordinate of the player's plane (follows mouse movement)
    private List<MobUnit> playerMob = new ArrayList<>(); // List to store player's bullets (encapsulation of bullet objects)
    private List<EnemyUnit> enemyMob = new ArrayList<>(); // List to store enemy units (encapsulation of enemy objects)
    private List<BossProjectile> bossProjectiles = new ArrayList<>(); // List to store BOSS's bullets (separate from normal bullets for modular management)
    private List<Gate> gates = new ArrayList<>(); // List to store evolution gates (modular design for buff system)
    private List<Chest> chests = new ArrayList<>(); // List to store treasure chests (modular design for power-up system)

    // Game state flags (encapsulation of game status)
    private boolean isGameOver = false;   // Flag: True when game over (player loses)
    private boolean isVictory = false;    // Flag: True when player defeats BOSS (win)
    private boolean bossSpawned = false;  // Flag: True when BOSS is spawned
    private EnemyUnit finalBoss = null;   // Reference to the final BOSS object (polymorphism: same EnemyUnit class, different behavior)

    // Game progress variables
    private int score = 0;                // Player's score (increments by defeating enemies)
    private int playerHP = 2;             // Player's health points (2 lives by default)
    private int invincibleTimer = 0;      // Invincibility frame timer: Prevents repeated damage after being hit
    private double difficultyMultiplier = 1.0; // Difficulty multiplier: Increases with score (dynamic difficulty)
    private int baseFireCount = 1;        // Base number of bullets fired per shot (increases by opening chests)

    // Buff system variables (encapsulation of temporary power-ups)
    private int scatterBuffTimer = 0;     // Timer for Scatter Buff (counts down to 0 when buff expires)
    private int dmgBuffTimer = 0;         // Timer for Damage Buff (counts down to 0 when buff expires)
    private int giantBuffTimer = 0;       // Timer for Giant Bullet Buff (counts down to 0 when buff expires)
    private static final int BUFF_DURATION = 420; // Buff duration (420 frames = 7 seconds at 60FPS)
    private boolean hasScatterBuff = false; // Flag: True when Scatter Buff is active
    private boolean hasDmgBuff = false;   // Flag: True when Damage Buff is active
    private boolean hasGiantBuff = false; // Flag: True when Giant Bullet Buff is active

    // Utility objects (encapsulated for reuse)
    private Random random = new Random(); // Random number generator for spawning enemies/chests
    // Timers for controlling spawn intervals (prevents spawning too frequently)
    private long lastFireTime = 0, lastGateSpawnTime = 0, lastHordeSpawnTime = 0;
    private GraphicsContext gc;           // JavaFX GraphicsContext: Used for drawing all game elements

    // Image resources (static so internal classes can access them)
    public static Image playerPlaneImage; // Player's plane sprite
    public static Image background;       // Reserved background image (not used in current version)
    private int engineFireTimer = 0;      // Timer for engine flame animation (controls blinking frequency)
    private boolean isEngineFireBright = false; // Flag: Controls engine flame brightness (blinking effect)

    // Scrolling road background variables (visual enhancement)
    private Image roadBgImage;            // Top-down road background sprite
    private double bgY1 = 0;              // Y-coordinate of the first background image (for seamless scrolling)
    private double bgY2 = 0;              // Y-coordinate of the second background image (for seamless scrolling)
    private double bgSpeed = 5;           // Background scrolling speed (higher = faster road movement)

    // Enemy and bullet sprites (modular design for visual customization)
    private Image enemyImage1;            // Sprite for enemy type 1
    private Image enemyImage2;            // Sprite for enemy type 2
    private Image bulletGiant;            // Sprite for Giant Bullet (Buff-activated)
    private Image bulletNormal;           // Sprite for Normal Bullet (default)
    private Image bulletScatter;          // Sprite for Scatter Bullet (Buff-activated)
    private Image bulletDamage;           // Sprite for High-Damage Bullet (Buff-activated)
    private Image bossImage;              // Sprite for the final BOSS
    private Image chestImage;             // Sprite for treasure chests
    private Image bossBulletImage;        // Sprite for BOSS's bullets

    // Audio resources (enhance game experience)
    private AudioClip shootSound;         // Sound effect for shooting
    private MediaPlayer bgmPlayer;        // Background music player

    // --- Inner Class: Player's Bullet (MobUnit) ---
    /**
     * Encapsulates the player's bullet objects (OOP: Encapsulation)
     * Contains attributes (position, velocity, damage, size) and methods (update, draw)
     * Polymorphism: Different bullet behaviors are handled via Buff flags in the draw method
     */
    class MobUnit {
        double x, y;       // X and Y coordinates of the bullet
        double vx, vy;     // Horizontal and vertical velocity (controls bullet direction and speed)
        double damage;     // Damage value of the bullet (varies with Buffs)
        double size;       // Size of the bullet (varies with Buffs)
        boolean hasPassedGate = false; // Flag: True if the bullet has passed through an ATK gate (prevents repeated buffing)

        /**
         * Constructor for MobUnit (player's bullet)
         * @param x Initial X coordinate
         * @param y Initial Y coordinate
         * @param vx Horizontal velocity (positive = right, negative = left)
         * @param vy Vertical velocity (negative = up, positive = down)
         * @param dmg Damage value of the bullet
         * @param sz Size of the bullet (pixels)
         */
        MobUnit(double x, double y, double vx, double vy, double dmg, double sz) {
            this.x = x;
            this.y = y;
            this.vx = vx;
            this.vy = vy;
            this.damage = dmg;
            this.size = sz;
        }

        /**
         * Update bullet position per frame
         * Handles boundary collision: Bullet bounces horizontally when hitting left/right edges
         */
        void update() {
            x += vx;
            y += vy;
            // Bounce off left/right edges of the screen
            if (x < 0 || x > WIDTH - 10) {
                vx = -vx;
            }
        }

        /**
         * Draw the bullet on the canvas (OOP: Polymorphism via Buff states)
         * Selects bullet sprite and laser color based on active Buffs
         * Adds visual effects (glow, stroke) to enhance player experience
         * @param gc GraphicsContext for drawing
         * @param game Reference to the main game class (to access Buff flags and images)
         */
        void draw(GraphicsContext gc, ThunderFighter game) {
            Image useBulletImage = null;
            Color laserColor; // Laser color (matches bullet type for visual consistency)

            // 1. Select bullet sprite and laser color based on active Buffs (polymorphism)
            if (game.giantBuffTimer > 0) {
                useBulletImage = game.bulletGiant;
                laserColor = Color.rgb(255, 215, 0); // Gold color for Giant Bullet
            } else if (game.scatterBuffTimer > 0) {
                useBulletImage = game.bulletScatter;
                laserColor = Color.rgb(180, 0, 255); // Purple color for Scatter Bullet
            } else if (game.dmgBuffTimer > 0) {
                useBulletImage = game.bulletDamage;
                laserColor = Color.rgb(0, 190, 255); // Cyan color for High-Damage Bullet
            } else {
                useBulletImage = game.bulletNormal;
                laserColor = Color.rgb(255, 50, 50); // Red color for Normal Bullet
            }

            double drawSize = this.size; // Use the bullet's size (varies with Buffs)

            // --- Visual Effects: Glow, Sprite, and Stroke ---
            // 2. Draw outer glow (simulates laser brightness, enhances visual appeal)
            gc.save(); // Save current GraphicsContext state (avoids affecting other draws)
            gc.setGlobalAlpha(0.4); // Transparency for soft glow
            gc.setFill(laserColor);
            double glowSize = drawSize + 10; // Glow is larger than the bullet
            gc.fillOval(this.x - glowSize/2, this.y - glowSize/2, glowSize, glowSize);
            gc.restore(); // Restore original state

            // 3. Draw bullet sprite (fallback to solid circle if image fails to load)
            if (useBulletImage != null && !useBulletImage.isError()) {
                // Center the sprite on the bullet's (x,y) coordinate
                gc.drawImage(useBulletImage,
                        this.x - drawSize/2,
                        this.y - drawSize/2,
                        drawSize,
                        drawSize);
            } else {
                // Fallback: Draw solid circle if image is missing (ensures game functionality)
                gc.setFill(laserColor);
                gc.fillOval(this.x, this.y, drawSize, drawSize);
            }

            // 4. Draw outline stroke (highlights bullet shape, improves visibility)
            gc.setStroke(laserColor.brighter()); // Brighter color for contrast
            gc.setLineWidth(1); // Thin stroke for sharpness
            gc.strokeOval(this.x - drawSize/2, this.y - drawSize/2, drawSize, drawSize);
        }
    }

    // --- Inner Class: BOSS's Projectile ---
    /**
     * Encapsulates the final BOSS's bullet objects (OOP: Encapsulation)
     * Separated from player's bullets for modular management (different behavior and collision rules)
     */
    class BossProjectile {
        double x, y;       // X and Y coordinates of the BOSS bullet
        double vx, vy;     // Horizontal and vertical velocity (tracks player's position)
        double size = 35;  // Size of BOSS bullet (larger than normal bullets for visibility)

        /**
         * Constructor for BossProjectile
         * @param x Initial X coordinate (spawns at BOSS's cannon position)
         * @param y Initial Y coordinate (spawns at BOSS's bottom edge)
         * @param vx Horizontal velocity (calculated to track player)
         * @param vy Vertical velocity (downward, constant speed)
         */
        BossProjectile(double x, double y, double vx, double vy) {
            this.x = x;
            this.y = y;
            this.vx = vx;
            this.vy = vy;
        }

        /**
         * Update BOSS bullet position per frame (follows player's horizontal movement)
         */
        void update() {
            x += vx;
            y += vy;
        }

        /**
         * Draw BOSS bullet on the canvas (fallback to solid circle if image fails)
         * @param gc GraphicsContext for drawing
         */
        void draw(GraphicsContext gc) {
            // 1. Draw sprite if available (priority: visual consistency)
            if (bossBulletImage != null && !bossBulletImage.isError()) {
                gc.drawImage(bossBulletImage, x, y, size, size);
            } else {
                // 2. Fallback: Draw purple circle with outline (ensures functionality)
                gc.setFill(Color.web("#8A2BE2")); // Dark purple core
                gc.fillOval(x, y, size, size);
                gc.setStroke(Color.web("#EE82EE")); // Light purple outline
                gc.setLineWidth(3);
                gc.strokeOval(x, y, size, size);
            }
        }
    }

    // --- Inner Class: Enemy Unit (Including BOSS) ---
    /**
     * Encapsulates enemy units (normal enemies and BOSS) (OOP: Polymorphism)
     * Normal enemies and BOSS share the same base class but have different behaviors (update method)
     * Contains attributes (position, size, health) and methods (update, draw, pushBackBullets)
     */
    class EnemyUnit {
        double x, y;       // X and Y coordinates of the enemy
        double size;       // Size of the enemy (larger for BOSS)
        double hp;         // Current health points
        double maxHp;      // Maximum health points (for health bar display)
        boolean isBoss;    // Flag: True if this is the final BOSS (triggers unique behavior)
        int attackCooldown = 0; // Cooldown timer for BOSS attacks (prevents spamming)
        int roarTimer = 0; // Timer for BOSS's "roar" ability (pushes back player's bullets)
        private int enemyType; // Type of normal enemy (0 or 1, uses different sprites)

        /**
         * Constructor for EnemyUnit (handles both normal enemies and BOSS)
         * @param x Initial X coordinate
         * @param y Initial Y coordinate
         * @param isBoss Flag: True if creating a BOSS, false for normal enemies
         */
        EnemyUnit(double x, double y, boolean isBoss) {
            this.x = x;
            this.y = y;
            this.isBoss = isBoss;

            // Initialize normal enemy properties (polymorphism: different from BOSS)
            if (!isBoss) {
                this.enemyType = random.nextInt(2); // Randomly select enemy type (0 or 1)
                this.size = 65; // Fixed size for normal enemies (matches sprite dimensions)
                // Dynamic health: Increases with score and difficulty multiplier (progressive difficulty)
                double baseHp = 8; // Base health (adjustable: 5-10 for balanced early game)
                double scoreBonus = score / 60.0; // Health scales with player's progress
                this.maxHp = (1.2 + (score / 150.0)) * difficultyMultiplier;
            } else {
                // Initialize BOSS properties (polymorphism: unique from normal enemies)
                this.size = 180; // Larger size for BOSS (visually dominant)
                this.maxHp = 2500 * difficultyMultiplier; // High health for challenging fight
            }
            this.hp = maxHp; // Set current health to maximum on spawn
        }

        /**
         * Update enemy state per frame (OOP: Polymorphism - different logic for BOSS/normal enemies)
         * Handles movement, attack, and ability cooldowns
         */
        void update() {
            if (isBoss) {
                // BOSS movement: Slow horizontal oscillation (sin wave) + downward spawn
                x += Math.sin(System.currentTimeMillis() / 1200.0) * 1.2;
                if (y < 70) { // BOSS spawns off-screen top, moves down to 70px Y
                    y += 0.4;
                }
                // Keep BOSS within screen boundaries (prevents off-screen escape)
                if (x < 0) {
                    x = 0;
                }
                if (x > WIDTH - size) {
                    x = WIDTH - size;
                }

                // --- BOSS Unique Abilities ---
                roarTimer++;     // Increment roar ability timer
                attackCooldown++; // Increment attack cooldown timer

                // 1. "Roar" ability: Pushes back all player bullets within range (area control)
                if (roarTimer > 180) { // Activate every 180 frames (3 seconds at 60FPS)
                    roarTimer = 0; // Reset timer
                    pushBackBullets(); // Trigger bullet pushback effect
                }

                // 2. Tracking Bullets: Dual-shot homing bullets (targets player's position)
                if (attackCooldown > 100) { // Fire every 100 frames (1.67 seconds at 60FPS)
                    attackCooldown = 0; // Reset cooldown
                    double targetX = cannonX; // Target player's current X coordinate
                    double bossBottomY = y + size; // Spawn bullets at BOSS's bottom edge
                    double bulletSpeedY = 5.0; // Vertical speed of BOSS bullets (faster than normal)

                    // Dual-shot logic: Spawn two bullets (left and right cannons of BOSS)
                    // Left cannon bullet
                    double originX1 = x + 30; // Left spawn point (adjusted for BOSS sprite)
                    // Calculate horizontal velocity to track player (smooth homing)
                    double dx1 = (targetX - originX1) / (HEIGHT / bulletSpeedY * 0.8);

                    // Right cannon bullet
                    double originX2 = x + size - 30; // Right spawn point (adjusted for BOSS sprite)
                    double dx2 = (targetX - originX2) / (HEIGHT / bulletSpeedY * 0.8);

                    // Add both bullets to the BOSS projectile list (modular management)
                    bossProjectiles.add(new BossProjectile(originX1, bossBottomY, dx1, bulletSpeedY));
                    bossProjectiles.add(new BossProjectile(originX2, bossBottomY, dx2, bulletSpeedY));
                }
            } else {
                // Normal enemy movement: Constant downward movement (simple, consistent)
                y += 0.5;
            }
        }

        /**
         * BOSS's "Roar" ability: Pushes back player's bullets in a large radius
         * Adds visual effect (red circle) to indicate ability activation
         */
        private void pushBackBullets() {
            // Draw red outline circle (visual feedback for ability activation)
            gc.setStroke(Color.RED);
            gc.setLineWidth(5);
            gc.strokeOval(x - 50, y - 50, size + 100, size + 100);

            // Push back all player bullets within 250px radius of BOSS
            for (MobUnit u : playerMob) {
                // Calculate distance between bullet and BOSS center
                double dx = u.x - (x + size/2);
                double dy = u.y - (y + size/2);
                double dist = Math.sqrt(dx*dx + dy*dy);

                // If bullet is within range, push it back (reverse direction + speed boost)
                if (dist < 250) {
                    u.vx = (dx / dist) * 8; // Horizontal push (away from BOSS)
                    u.vy = Math.abs(u.vy); // Vertical push (downward, away from BOSS)
                }
            }
        }

        /**
         * Draw enemy on the canvas (OOP: Polymorphism - different visuals for BOSS/normal enemies)
         * Includes health bar, sprite, and ability effects (roar warning)
         * @param gc GraphicsContext for drawing
         */
        void draw(GraphicsContext gc) {
            double hpRatio = Math.max(0, hp / maxHp); // Health ratio (0.0 to 1.0) for health bar

            if (isBoss) {
                // Draw BOSS sprite (fallback to solid rectangle if image fails)
                if (bossImage != null && !bossImage.isError()) {
                    gc.drawImage(bossImage, x, y, size, size); // Draw BOSS sprite (180x180)
                } else {
                    // Fallback: Dark blue rectangle (ensures BOSS is visible)
                    gc.setFill(Color.DARKSLATEBLUE);
                    gc.fillRect(x, y, size, size);
                }

                // 2. Roar ability visual warning: Red outline when ability is about to activate
                if (roarTimer > 120) { // Show warning in the last 60 frames of roar cooldown
                    gc.setStroke(Color.RED);
                    gc.setLineWidth(3);
                    // Outline slightly larger than BOSS sprite for visibility
                    gc.strokeRect(x - 5, y - 5, size + 10, size + 10);
                }

                // 3. Health bar: Draw below BOSS (centered, visual feedback for progress)
                double bloodBarWidth = size * 0.7; // Health bar width = 70% of BOSS size
                double bloodBarX = x + (size - bloodBarWidth) / 2; // Center health bar horizontally
                // Gray background (max health)
                gc.setFill(Color.GRAY);
                gc.fillRect(bloodBarX, y - 8, bloodBarWidth, 5);
                // Green foreground (current health, scales with hpRatio)
                gc.setFill(Color.LIME);
                gc.fillRect(bloodBarX, y - 8, bloodBarWidth * hpRatio, 5);
            } else {
                // Draw normal enemy (random sprite selection + health bar)
                boolean isImage1Valid = enemyImage1 != null && !enemyImage1.isError();
                boolean isImage2Valid = enemyImage2 != null && !enemyImage2.isError();

                // Draw sprite if available (select based on enemyType)
                if (isImage1Valid && isImage2Valid) {
                    if (enemyType == 0) {
                        gc.drawImage(enemyImage1, x, y, 65, 65); // Draw enemy type 1 sprite
                    } else {
                        gc.drawImage(enemyImage2, x, y, 65, 65); // Draw enemy type 2 sprite
                    }
                } else {
                    // Fallback: Solid rectangle (color fades with health)
                    gc.setFill(Color.color(1.0, 0.2 * hpRatio, 0.2 * hpRatio)); // Red → Dark red as health drops
                    gc.fillRect(x, y, size, size);
                }

                // Health bar for normal enemies (centered above sprite)
                double bloodBarWidth = size * 0.7; // 70% of enemy size for health bar
                double bloodBarX = x + (size - bloodBarWidth) / 2; // Horizontal center
                // Gray background (max health)
                gc.setFill(Color.GRAY);
                gc.fillRect(bloodBarX, y - 8, bloodBarWidth, 5);
                // Green foreground (current health)
                gc.setFill(Color.LIME);
                gc.fillRect(bloodBarX, y - 8, bloodBarWidth * hpRatio, 5);
            }
        }
    }

    // --- Inner Class: Treasure Chest ---
    /**
     * Encapsulates treasure chest objects (OOP: Encapsulation)
     * When destroyed by player's bullets, increases base fire count (permanent power-up)
     * Contains health (requires multiple hits to open) and visual properties
     */
    class Chest {
        double x, y;       // X and Y coordinates of the chest
        double size = 80;  // Size of the chest (adjusted for visibility)
        double hp = 5;     // Health: Requires 5 bullet hits to open (balanced challenge)

        /**
         * Constructor for Chest
         * @param x Initial X coordinate (random within screen width)
         * @param y Initial Y coordinate (spawns off-screen top)
         */
        Chest(double x, double y) {
            this.x = x;
            this.y = y;
        }

        /**
         * Update chest position per frame (constant downward movement)
         */
        void update() {
            y += 1.5; // Faster than normal enemies (encourages player to prioritize)
        }

        /**
         * Draw chest on the canvas (sprite + health text)
         * @param gc GraphicsContext for drawing
         */
        void draw(GraphicsContext gc) {
            // 1. Draw chest sprite (fallback to gold rectangle if image fails)
            if (chestImage != null && !chestImage.isError()) {
                gc.drawImage(chestImage, x, y, size, size);
            } else {
                gc.setFill(Color.GOLD);
                gc.fillRect(x, y, size, size);
            }

            // 2. Draw health text (below chest, white bold font for visibility)
            gc.setFill(Color.WHITE);
            gc.setFont(Font.font("Arial", FontWeight.BOLD, 14)); // Bold font for readability
            // Center text horizontally below the chest
            gc.fillText("HP:" + (int)hp, x + size/2 - 15, y + size + 15);
        }
    }

    // --- Inner Class: Evolution Gate ---
    /**
     * Encapsulates evolution gate objects (OOP: Encapsulation)
     * Two types: Purple (BURST/GIANT Buff) and Blue (ATK x2 Buff)
     * Purple gates require bullet "charging" (collect X bullets) to activate
     * Blue gates apply ATK buff immediately when bullets pass through
     */
    class Gate {
        double x, y;       // X and Y coordinates of the gate
        double w, h;       // Width and height of the gate
        String op;         // Buff type for purple gates ("BURST" or "GIANT")
        boolean isPurple;  // Flag: True = purple gate (chargeable), False = blue gate (instant)
        int currentCharge = 0; // Current charge (for purple gates: bullets collected)
        int maxCharge;     // Required charge to activate purple gate

        /**
         * Constructor for Gate
         * @param x Initial X coordinate
         * @param y Initial Y coordinate (spawns off-screen top)
         * @param w Width of the gate
         * @param op Buff type (for purple) or "ATK" (for blue)
         * @param isPurple Flag: True = purple gate, False = blue gate
         * @param maxCharge Required charge for purple gate (ignored for blue)
         */
        Gate(double x, double y, double w, String op, boolean isPurple, int maxCharge) {
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = 60; // Fixed height for gates (consistent visual size)
            this.op = op;
            this.isPurple = isPurple;
            this.maxCharge = maxCharge;
        }

        /**
         * Update gate position per frame (constant downward movement)
         */
        void update() {
            y += 2.2; // Faster than enemies/chests (encourages quick decision-making)
        }

        /**
         * Draw gate on the canvas (transparent color + text + outline)
         * @param gc GraphicsContext for drawing
         */
        void draw(GraphicsContext gc) {
            // Draw semi-transparent rectangle (purple for chargeable, blue for instant)
            gc.setFill(isPurple ? Color.rgb(180, 50, 255, 0.7) : Color.rgb(0, 80, 200, 0.6));
            gc.fillRect(x, y, w, h);
            // White outline for visibility
            gc.setStroke(Color.WHITE);
            gc.strokeRect(x, y, w, h);
            // Draw text (buff type + charge progress for purple gates)
            gc.setFill(Color.WHITE);
            gc.setFont(Font.font("Arial", FontWeight.BOLD, 18)); // Bold font for readability
            String display = isPurple ? op + ": " + currentCharge + "/" + maxCharge : "ATK x 2";
            // Center text horizontally and vertically in the gate
            gc.fillText(display, x + w / 2 - 45, y + h / 2 + 7);
        }
    }

    /**
     * Override JavaFX Application's start method (entry point for GUI)
     * Initializes game window, resources (images/audio), and starts animation loop
     * @param stage JavaFX Stage (main window)
     */
    @Override
    public void start(Stage stage) {
        // Initialize JavaFX UI components (Pane + Canvas)
        Pane root = new Pane();
        Canvas canvas = new Canvas(WIDTH, HEIGHT);
        gc = canvas.getGraphicsContext2D(); // Get GraphicsContext for drawing
        root.getChildren().add(canvas);

        // Create game scene (fixed size, matches canvas)
        Scene scene = new Scene(root, WIDTH, HEIGHT);
        // Bind player's plane X position to mouse movement (intuitive control)
        scene.setOnMouseMoved(e -> cannonX = e.getX());

        try {
            // Load image resources (sprites for player, enemies, bullets, etc.)
            playerPlaneImage = new Image("plane.png");
            enemyImage1 = new Image("enemy1.png");
            enemyImage2 = new Image("enemy2.png");
            roadBgImage = new Image("Road.png"); // Top-down road background
            bulletGiant = new Image("bulletGiant.png");   // Giant Bullet sprite
            bulletNormal = new Image("bulletNormal.png"); // Normal Bullet sprite
            bulletScatter = new Image("bulletScatter.png"); // Scatter Bullet sprite
            bulletDamage = new Image("bulletDamage.png"); // High-Damage Bullet sprite
            bossImage = new Image("boss.png"); // BOSS sprite
            chestImage = new Image("chest.png"); // Treasure Chest sprite
            bossBulletImage = new Image("bossBullet.png"); // BOSS Bullet sprite

            // Load shooting sound effect (WAV format)
            String soundPath = new File("Shoot.wav").toURI().toString();
            shootSound = new AudioClip(soundPath);
            shootSound.setVolume(0.2); // Set volume (0.0 = mute, 1.0 = max)

            System.out.println("Shoot sound loaded successfully!");

            // Load and play background music (BGM)
            String bgmPath = new File("bgm.wav").toURI().toString();
            Media bgmMedia = new Media(bgmPath);
            bgmPlayer = new MediaPlayer(bgmMedia);
            bgmPlayer.setCycleCount(MediaPlayer.INDEFINITE); // Loop BGM indefinitely
            bgmPlayer.setVolume(0.5); // BGM volume (lower than sound effects for balance)
            bgmPlayer.play(); // Start playing BGM

            System.out.println("BGM loaded and playing successfully!");

            // Initialize background positions for seamless scrolling
            if (roadBgImage != null && !roadBgImage.isError()) {
                bgY2 = -roadBgImage.getHeight(); // Second background starts above the first
            }

            // Validate enemy image loading (debug feedback for resource issues)
            if (enemyImage1.isError() || enemyImage2.isError()) {
                System.out.println("Enemy image loading failed: "
                        + (enemyImage1.isError() ? "enemy1 error: " + enemyImage1.getException().getMessage() : "")
                        + (enemyImage2.isError() ? "enemy2 error: " + enemyImage2.getException().getMessage() : ""));
            } else {
                System.out.println("Both enemy images loaded successfully!");
            }
        } catch (Exception e) {
            // Catch and print resource loading errors (debugging aid)
            System.out.println("Image/Audio loading exception: " + e.getMessage());
        }

        // Start JavaFX AnimationTimer (core game loop: runs 60 times per second)
        new AnimationTimer() {
            /**
             * Handle game logic and rendering per frame
             * @param now Current timestamp (nanoseconds, used for timing)
             */
            @Override
            public void handle(long now) {
                // Clear screen with black background (prevents trail artifacts)
                gc.setFill(Color.BLACK);
                gc.fillRect(0, 0, WIDTH, HEIGHT);

                // --- Seamless Scrolling Background ---
                if (roadBgImage != null && !roadBgImage.isError()) {
                    // Draw two background images (for infinite scrolling)
                    gc.drawImage(roadBgImage, 0, bgY1, WIDTH, roadBgImage.getHeight());
                    gc.drawImage(roadBgImage, 0, bgY2, WIDTH, roadBgImage.getHeight());

                    // Update background positions (scroll downward)
                    bgY1 += bgSpeed;
                    bgY2 += bgSpeed;

                    // Reset background positions when they scroll off-screen (seamless loop)
                    double bgHeight = roadBgImage.getHeight();
                    if (bgY1 >= HEIGHT) {
                        bgY1 = -bgHeight;
                    }
                    if (bgY2 >= HEIGHT) {
                        bgY2 = -bgHeight;
                    }
                }

                // Draw static background elements (cleanup line + dead line)
                drawBackground();

                // If game over or victory, draw result screen and exit loop
                if (isGameOver || isVictory) {
                    drawResult();
                    return;
                }

                // Core game logic (processed in order per frame)
                handleFiring(now);          // Player shooting logic
                handleGates(now);           // Evolution gate spawning and updates
                handleChests(now);          // Treasure chest spawning and updates
                handlePlayerUnits();        // Player bullet updates and collisions
                handleEnemyHorde(now);      // Enemy spawning and updates
                handleBossProjectiles();    // BOSS bullet updates and collisions
                checkCombatAndGameOver();   // Collision detection (player-enemy/bullets) and game state checks
                drawUI();                   // Draw game UI (score, HP, buffs, etc.)

                // Update difficulty multiplier (scales with score: progressive challenge)
                difficultyMultiplier = 1.0 + (score / 4000.0);

                // Spawn BOSS when score reaches trigger and BOSS not yet spawned
                if (score >= BOSS_TRIGGER_SCORE && !bossSpawned) {
                    spawnBoss();
                }

                // Decrement invincibility timer (if active)
                if (invincibleTimer > 0) {
                    invincibleTimer--;
                }

                // Update engine flame animation timer (controls blinking)
                engineFireTimer++;
                // Toggle flame brightness every 8 frames (adjust for faster/slower blinking)
                if (engineFireTimer % 8 == 0) {
                    isEngineFireBright = !isEngineFireBright;
                    engineFireTimer = 0; // Reset timer to avoid overflow
                }
            }
        }.start();

        // Configure and show main game window
        stage.setScene(scene);
        stage.setTitle("Thunder Fighter: BOSS Dual Strike Edition"); // English window title
        stage.show();
    }

    /**
     * Spawn the final BOSS (triggers when score reaches BOSS_TRIGGER_SCORE)
     * Clears all normal enemies to focus on BOSS fight
     */
    private void spawnBoss() {
        bossSpawned = true; // Mark BOSS as spawned
        enemyMob.clear();   // Clear all normal enemies (BOSS fight phase)
        // Spawn BOSS at center-top (off-screen, moves down to 70px Y)
        finalBoss = new EnemyUnit(WIDTH / 2.0 - 90, -200, true);
        enemyMob.add(finalBoss); // Add BOSS to enemy list (reuses enemy update/draw logic)
    }

    /**
     * Draw static background elements: Top cleanup line and bottom dead line
     * Visual guides for game mechanics (players learn boundaries)
     */
    private void drawBackground() {
        // Top cleanup line (cyan, semi-transparent)
        gc.setStroke(Color.rgb(0, 200, 255, 0.3));
        gc.strokeLine(0, TOP_CLEANUP_LINE, WIDTH, TOP_CLEANUP_LINE);
        // Bottom dead line (red, semi-transparent)
        gc.setStroke(Color.rgb(150, 0, 0, 0.5));
        gc.strokeLine(0, DEAD_LINE, WIDTH, DEAD_LINE);
    }

    /**
     * Handle player shooting logic: Controls bullet spawning, Buff timers, and visual effects
     * @param now Current timestamp (nanoseconds, used to control fire rate)
     */
    private void handleFiring(long now) {
        boolean isFiring = false; // Flag: True if player is shooting this frame

        // Control fire rate: 240ms cooldown (prevents spamming)
        if (now - lastFireTime > 240 * 1_000_000L) {
            // Play shooting sound effect (if loaded)
            if (shootSound != null) {
                shootSound.play();
            }

            // Determine bullet damage and size based on active Buffs
            double currentDmg = (dmgBuffTimer > 0) ? 2.0 : 1.0; // Double damage with Damage Buff
            double currentSize = (giantBuffTimer > 0) ? 36.0 : 15.0; // Larger size with Giant Buff

            // Spawn bullets based on base fire count (increases with chests)
            for (int i = 0; i < baseFireCount; i++) {
                // Horizontal offset for multiple bullets (spreads left/right)
                double xOffset = (i - (baseFireCount - 1) / 2.0) * 12;

                // Scatter Buff: Spawn 3 bullets per fire (left/center/right)
                if (scatterBuffTimer > 0) {
                    for (int j = -1; j <= 1; j++) {
                        // Add scatter bullets to playerMob list (different horizontal velocities)
                        playerMob.add(new MobUnit(cannonX + xOffset, HEIGHT - 60, j * 2.2, -10.5, currentDmg, currentSize));
                    }
                } else {
                    // Normal fire: Spawn 1 bullet per fire (straight upward)
                    playerMob.add(new MobUnit(cannonX + xOffset, HEIGHT - 60, 0, -9.0, currentDmg, currentSize));
                }
            }

            // Update Buff timers (decrement if active, reset flags when expired)
            if (scatterBuffTimer > 0) {
                scatterBuffTimer--;
                if (scatterBuffTimer == 0) {
                    hasScatterBuff = false;
                }
            }
            if (dmgBuffTimer > 0) {
                dmgBuffTimer--;
                if (dmgBuffTimer == 0) {
                    hasDmgBuff = false;
                }
            }
            if (giantBuffTimer > 0) {
                giantBuffTimer--;
                if (giantBuffTimer == 0) {
                    hasGiantBuff = false;
                }
            }

            lastFireTime = now; // Update last fire time (control cooldown)
            isFiring = true; // Mark as firing (trigger muzzle flash effect)
        }

        // Draw player's plane (with invincibility blinking and engine flame)
        if (invincibleTimer % 4 == 0) { // Blink when invincible (visible every 4 frames)
            if (playerPlaneImage != null && !playerPlaneImage.isError()) {
                // Draw player plane sprite (centered on mouse X, fixed Y position)
                gc.drawImage(
                        playerPlaneImage,
                        cannonX - 50,    // X offset: Center sprite on mouse (sprite width = 100)
                        HEIGHT - 120,    // Y position: Fixed near bottom (avoids off-screen)
                        100,             // Sprite width (scaled to 100px)
                        100              // Sprite height (scaled to 100px, maintains aspect ratio)
                );

                // --- Engine Flame Animation (Blinking Effect) ---
                double engineX = cannonX - 2; // Engine X position (center of plane's tail)
                double engineY = (HEIGHT - 100) + 60; // Engine Y position (bottom of plane)

                // Toggle flame brightness based on isEngineFireBright flag
                if (isEngineFireBright) {
                    // Bright flame (3 layers: outer glow → core → center)
                    gc.setFill(Color.rgb(255, 105, 97, 0.4)); // Outer red glow (soft)
                    gc.fillOval(engineX - 17, engineY, 40, 50);
                    gc.setFill(Color.rgb(255, 165, 0, 0.7)); // Middle orange (core flame)
                    gc.fillOval(engineX - 12, engineY + 5, 30, 45);
                    gc.setFill(Color.rgb(255, 255, 0, 0.9)); // Inner yellow (bright center)
                    gc.fillOval(engineX - 8, engineY + 10, 20, 40);
                } else {
                    // Dim flame (3 layers: softer than bright state)
                    gc.setFill(Color.rgb(255, 90, 97, 0.2)); // Outer red glow (dim)
                    gc.fillOval(engineX - 17, engineY, 40, 40);
                    gc.setFill(Color.rgb(255, 140, 0, 0.4)); // Middle orange (dim)
                    gc.fillOval(engineX - 12, engineY + 5, 30, 35);
                    gc.setFill(Color.rgb(255, 220, 0, 0.6)); // Inner yellow (dim)
                    gc.fillOval(engineX - 8, engineY + 10, 20, 30);
                }

                // --- Muzzle Flash Effect (Triggers When Firing) ---
                if (isFiring) {
                    // Left cannon flash (3 layers: white core → yellow → blue)
                    gc.setFill(Color.WHITE);
                    gc.fillOval((cannonX - 50) + 22, (HEIGHT - 100) + 6, 4, 30);
                    gc.setFill(Color.rgb(255, 255, 0, 0.8));
                    gc.fillOval((cannonX - 50) + 20, (HEIGHT - 100) + 4, 8, 32);
                    gc.setFill(Color.rgb(20, 187, 225, 0.9));
                    gc.fillOval((cannonX - 50) + 20, (HEIGHT - 100) + 2, 10, 40);

                    // Right cannon flash (symmetric to left)
                    gc.setFill(Color.WHITE);
                    gc.fillOval((cannonX - 50) + 70, (HEIGHT - 100) + 6, 4, 30);
                    gc.setFill(Color.rgb(255, 255, 0, 0.8));
                    gc.fillOval((cannonX - 50) + 70, (HEIGHT - 100) + 4, 8, 32);
                    gc.setFill(Color.rgb(20, 187, 225, 0.9));
                    gc.fillOval((cannonX - 50) + 70, (HEIGHT - 100) + 2, 10, 40);

                    // Optional: Thin white outline for flash (enhances visibility)
                    gc.setStroke(Color.WHITE);
                    gc.setLineWidth(1);
                    gc.strokeOval((cannonX - 50) + 24, (HEIGHT - 100) + 25, 5, 10);
                    gc.strokeOval((cannonX - 50) + 70, (HEIGHT - 100) + 25, 5, 10);
                }
            } else {
                // Fallback: Draw solid rectangle if plane sprite fails (ensures playability)
                // Color changes with active Buffs (visual feedback)
                if (scatterBuffTimer > 0) {
                    gc.setFill(Color.GOLD);
                } else if (dmgBuffTimer > 0) {
                    gc.setFill(Color.RED);
                } else {
                    gc.setFill(Color.DODGERBLUE);
                }
                gc.fillRoundRect(cannonX - 20, HEIGHT - 60, 40, 40, 10, 10);
            }
        }
    }

    /**
     * Handle BOSS bullet updates and collisions with player
     * Removes bullets that go off-screen or hit the player
     */
    private void handleBossProjectiles() {
        // Use iterator to safely remove bullets during iteration
        Iterator<BossProjectile> it = bossProjectiles.iterator();
        while (it.hasNext()) {
            BossProjectile p = it.next();
            p.update(); // Update bullet position
            p.draw(gc); // Draw bullet

            // Collision detection: BOSS bullet hits player (if not invincible)
            if (invincibleTimer <= 0 && p.y > HEIGHT - 75 && Math.abs(p.x + p.size/2 - cannonX) < 30) {
                playerHP--; // Decrease player health
                invincibleTimer = 60; // Grant 1 second (60 frames) invincibility
                it.remove(); // Remove the bullet (prevents multiple hits)
                // Trigger game over if player health drops to 0 or below
                if (playerHP <= 0) {
                    isGameOver = true;
                }
            } else if (p.y > HEIGHT) {
                // Remove bullet if it goes off-screen bottom (saves memory)
                it.remove();
            }
        }
    }

    /**
     * Handle evolution gate spawning and updates
     * Spawns gates at fixed intervals (6 seconds) with random positions/types
     * @param now Current timestamp (nanoseconds, used to control spawn rate)
     */
    private void handleGates(long now) {
        // Spawn gate every 6 seconds (6000ms = 6000 * 1e6 nanoseconds)
        if (now - lastGateSpawnTime > 6000 * 1_000_000L) {
            // Dynamic charge requirement: Increases with score (progressive challenge)
            int currentReq = Math.min(35, 10 + (score / 120));
            boolean purpleOnLeft = random.nextBoolean(); // Randomly place purple gate on left/right
            String purpleMode = random.nextBoolean() ? "BURST" : "GIANT"; // Random purple gate type

            // Spawn two gates (split screen: left + right)
            if (purpleOnLeft) {
                // Left: Purple gate (BURST/GIANT), Right: Blue gate (ATK x2)
                gates.add(new Gate(0, -100, WIDTH / 2.0, purpleMode, true, currentReq));
                gates.add(new Gate(WIDTH / 2.0, -100, WIDTH / 2.0, "ATK", false, 0));
            } else {
                // Left: Blue gate (ATK x2), Right: Purple gate (BURST/GIANT)
                gates.add(new Gate(0, -100, WIDTH / 2.0, "ATK", false, 0));
                gates.add(new Gate(WIDTH / 2.0, -100, WIDTH / 2.0, purpleMode, true, currentReq));
            }

            lastGateSpawnTime = now; // Update last spawn time (control interval)
        }

        // Update and draw gates; remove gates that go off-screen (saves memory)
        gates.removeIf(g -> {
            g.update();
            g.draw(gc);
            return g.y > HEIGHT;
        });
    }

    /**
     * Handle treasure chest spawning and updates
     * Spawns chests randomly (1/850 chance per frame) for permanent power-ups
     * @param now Current timestamp (not used, but matches method signature for consistency)
     */
    private void handleChests(long now) {
        // Random spawn: 1/850 chance per frame (balanced rarity)
        if (random.nextInt(850) == 0) {
            // Spawn chest at random X (within screen width) and off-screen top Y
            chests.add(new Chest(random.nextDouble() * (WIDTH - 40), -50));
        }

        // Update and draw chests; remove chests that go off-screen (saves memory)
        chests.removeIf(c -> {
            c.update();
            c.draw(gc);
            return c.y > HEIGHT;
        });
    }

    /**
     * Handle player bullet updates, gate interactions, and chest interactions
     * Manages bullet lifecycle (spawn → update → collision → removal)
     */
    private void handlePlayerUnits() {
        List<MobUnit> newUnits = new ArrayList<>(); // Temporary list for BURST buff bullets
        Iterator<MobUnit> it = playerMob.iterator(); // Safe iteration for bullet removal

        while (it.hasNext()) {
            MobUnit u = it.next();
            u.update(); // Update bullet position
            u.draw(gc, this); // Draw bullet (polymorphic based on Buffs)

            // Remove bullets that go off-screen top (cleanup line)
            if (u.y < TOP_CLEANUP_LINE) {
                it.remove();
                continue;
            }
            // Remove bullets that go off-screen bottom (saves memory)
            if (u.y > HEIGHT) {
                it.remove();
                continue;
            }

            boolean removed = false; // Flag: True if bullet is removed (gate/chest interaction)

            // Check collision with evolution gates
            for (Gate g : gates) {
                // Bullet is inside the gate's bounds
                if (u.x > g.x && u.x < g.x + g.w && u.y < g.y + g.h && u.y > g.y) {
                    if (g.isPurple) {
                        // Purple gate: Charge up with bullet (increase charge count)
                        g.currentCharge++;
                        // Activate Buff if charge reaches max
                        if (g.currentCharge >= g.maxCharge) {
                            if (g.op.equals("BURST")) {
                                // BURST Buff: Spawn 35 spread bullets at gate position
                                triggerBurst(g.x + g.w/2, g.y + g.h/2, newUnits);
                                scatterBuffTimer = BUFF_DURATION*2; // Activate Scatter Buff
                                hasScatterBuff = true;
                            } else {
                                // GIANT Buff: Activate Giant Bullet Buff
                                giantBuffTimer = BUFF_DURATION;
                                hasGiantBuff = true;
                                // Reduce duration of other Buffs (prevents stacking)
                                scatterBuffTimer /= 2;
                                dmgBuffTimer /= 2;
                            }
                            g.y = 2000; // Move gate off-screen (remove after activation)
                        }
                        it.remove(); // Consume bullet for charging
                        removed = true;
                        break;
                    } else if (!u.hasPassedGate) {
                        // Blue gate: Apply ATK x2 Buff immediately (no charge needed)
                        u.hasPassedGate = true; // Mark bullet as having passed gate (prevents repeat buffing)
                        dmgBuffTimer = BUFF_DURATION; // Activate Damage Buff
                        hasDmgBuff = true;
                        // Reduce duration of other Buffs (prevents stacking)
                        scatterBuffTimer /= 2;
                        giantBuffTimer /= 2;
                    }
                }
            }

            // If bullet not removed by gate, check collision with treasure chests
            if (!removed) {
                for (Chest c : chests) {
                    // Bullet is inside the chest's bounds
                    if (u.x > c.x && u.x < c.x + c.size && u.y > c.y && u.y < c.y + c.size) {
                        c.hp -= u.damage; // Reduce chest health
                        it.remove(); // Consume bullet on hit
                        removed = true;
                        // Open chest if health drops to 0 or below (permanent fire count increase)
                        if (c.hp <= 0) {
                            baseFireCount++; // Increase base bullets per shot
                            c.y = 2000; // Move chest off-screen (remove after opening)
                        }
                        break;
                    }
                }
            }
        }

        // Add BURST buff bullets to playerMob (limit to 600 to prevent memory overload)
        if (playerMob.size() < 600) {
            playerMob.addAll(newUnits);
        }
    }

    /**
     * Handle enemy spawning and updates (normal enemies and BOSS)
     * Spawns enemy hordes at fixed intervals (2 seconds) when BOSS not spawned
     * @param now Current timestamp (nanoseconds, used to control spawn rate)
     */
    private void handleEnemyHorde(long now) {
        // If BOSS is spawned, only update BOSS (ignore normal enemy logic)
        if (bossSpawned) {
            if (finalBoss != null) {
                finalBoss.update();
                finalBoss.draw(gc);
            }
            return;
        }

        // Spawn enemy horde every 2 seconds (2000ms = 2000 * 1e6 nanoseconds)
        if (now - lastHordeSpawnTime > 2000 * 1_000_000L) {
            // Spawn 8 enemies (one per 1/8 screen width)
            for (int i = 0; i < 8; i++) {
                // 60% chance to spawn an enemy in each position (varied hordes)
                if (random.nextInt(10) < 6) {
                    enemyMob.add(new EnemyUnit(i * (WIDTH / 8.0) + 2, -50, false));
                }
            }
            lastHordeSpawnTime = now; // Update last spawn time (control interval)
        }

        // Update and draw all normal enemies
        enemyMob.forEach(e -> {
            e.update();
            e.draw(gc);
        });
    }

    /**
     * Core collision detection: Player bullets vs enemies/BOSS
     * Checks game over conditions (enemies cross dead line) and victory (BOSS defeated)
     */
    private void checkCombatAndGameOver() {
        // Iterate over enemies (safe removal with iterator)
        Iterator<EnemyUnit> eIt = enemyMob.iterator();
        while (eIt.hasNext()) {
            EnemyUnit e = eIt.next();

            // Game over: Enemy crosses dead line (reaches bottom safe zone)
            if (e.y + e.size > DEAD_LINE) {
                isGameOver = true;
                return;
            }

            // Check collision between player bullets and current enemy
            Iterator<MobUnit> pIt = playerMob.iterator();
            while (pIt.hasNext()) {
                MobUnit p = pIt.next();
                // Bullet is inside enemy's bounds (collision detected)
                if (p.x > e.x && p.x < e.x + e.size && p.y > e.y && p.y < e.y + e.size) {
                    e.hp -= p.damage; // Reduce enemy health
                    pIt.remove(); // Remove bullet after hit (prevents multiple hits)

                    // Enemy defeated: Check if health drops to 0 or below
                    if (e.hp <= 0) {
                        if (e.isBoss) {
                            isVictory = true; // Victory if BOSS is defeated
                        }
                        // Add score (1000 for BOSS, 20 for normal enemies)
                        score += e.isBoss ? 1000 : 20;
                        e.hp = -100; // Mark enemy for removal (avoids repeated checks)
                        break;
                    }
                }
            }

            // Remove defeated enemy from list (saves memory)
            if (e.hp == -100) {
                eIt.remove();
            }
        }
    }

    /**
     * BURST Buff effect: Spawns 35 spread bullets in a 60-degree arc
     * Creates a wide-area attack for clearing groups of enemies
     * @param x Spawn X coordinate (center of the evolution gate)
     * @param y Spawn Y coordinate (center of the evolution gate)
     * @param newUnits Temporary list to store BURST bullets (avoids concurrent modification)
     */
    private void triggerBurst(double x, double y, List<MobUnit> newUnits) {
        for (int i = 0; i < 35; i++) {
            // Random angle between 240° and 300° (downward arc, covers most of the screen)
            double angle = 240 + random.nextDouble() * 60;
            // Calculate bullet velocity based on angle (spread in arc)
            double vx = Math.cos(Math.toRadians(angle)) * 12;
            double vy = Math.sin(Math.toRadians(angle)) * 12;
            // Add BURST bullet to temporary list (small size, low damage)
            newUnits.add(new MobUnit(x, y, vx, vy, 1.0, 9.0));
        }
    }

    /**
     * Draw game UI (user interface) elements: Score, HP, Buffs, and evolution threshold
     * Provides real-time feedback to the player (critical for gameplay)
     */
    private void drawUI() {
        // Draw score and progress (yellow bold font, top-left)
        gc.setFill(Color.YELLOW);
        gc.setFont(Font.font("Arial", FontWeight.BOLD, 16));
        gc.fillText("Forces: " + playerMob.size(), 20, 30); // Number of player bullets
        gc.fillText("Progress: " + score + " / " + BOSS_TRIGGER_SCORE, 20, 50); // Score to BOSS

        // Draw player HP (red bold font, top-left)
        gc.setFill(Color.RED);
        String hearts = "";
        for (int i = 0; i < playerHP; i++) {
            hearts += "❤ "; // Heart symbol for HP (intuitive visual)
        }
        gc.fillText("HP: " + hearts, 20, 75);

        // Draw evolution gate charge requirement (purple bold font, top-right)
        int currentReq = Math.min(35, 10 + (score / 120));
        gc.setFill(Color.VIOLET);
        gc.fillText("Evo Threshold: " + currentReq, WIDTH - 120, 30);

        // Draw active Buffs (colored bold font, top-left below HP)
        if (scatterBuffTimer > 0) {
            gc.setFill(Color.GOLD);
            gc.fillText("BUFF: Scatter Burst!", 20, 100);
        }
        if (dmgBuffTimer > 0) {
            gc.setFill(Color.RED);
            gc.fillText("BUFF: Damage Boost!", 20, 120);
        }
        if (giantBuffTimer > 0) {
            gc.setFill(Color.VIOLET);
            gc.fillText("BUFF: Giant Bullets!", 20, 140);
        }
    }

    /**
     * Draw game result screen (win/lose)
     * Overlays a semi-transparent black background with large result text
     */
    private void drawResult() {
        // Semi-transparent black background (darkens screen for focus)
        gc.setFill(Color.rgb(0, 0, 0, 0.8));
        gc.fillRect(0, 0, WIDTH, HEIGHT);

        // Draw result text (large bold font, centered)
        gc.setFont(Font.font("Arial", FontWeight.BOLD, 60));
        if (isVictory) {
            gc.setFill(Color.GOLD);
            gc.fillText("YOU WIN!", WIDTH / 2.0 - 140, HEIGHT / 2.0); // Victory text
        } else {
            gc.setFill(Color.RED);
            gc.fillText("FAILED", WIDTH / 2.0 - 100, HEIGHT / 2.0); // Game over text
        }
    }

    /**
     * Main method: Entry point for the Java application
     * Sets JavaFX rendering pipeline to software (avoids hardware compatibility issues)
     * Launches JavaFX application
     * @param args Command line arguments (not used)
     */
    public static void main(String[] args) {
        System.setProperty("prism.order", "sw"); // Force software rendering (compatibility)
        launch(args); // Launch JavaFX application (calls start() method)
    }
}

