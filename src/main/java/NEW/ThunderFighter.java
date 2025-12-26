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

public class ThunderFighter extends Application {
    private static final int WIDTH = 480;
    private static final int HEIGHT = 800;
    private static final int DEAD_LINE = HEIGHT - 120;
    private static final int TOP_CLEANUP_LINE = 50;
    private static final int BOSS_TRIGGER_SCORE = 3000;

    private double cannonX = WIDTH / 2.0;
    private List<MobUnit> playerMob = new ArrayList<>();
    private List<EnemyUnit> enemyMob = new ArrayList<>();
    private List<BossProjectile> bossProjectiles = new ArrayList<>(); // Boss 子弹列表
    private List<Gate> gates = new ArrayList<>();
    private List<Chest> chests = new ArrayList<>();

    private boolean isGameOver = false;
    private boolean isVictory = false;
    private boolean bossSpawned = false;
    private EnemyUnit finalBoss = null;

    private int score = 0;
    private int playerHP = 2; // 玩家生命值
    private int invincibleTimer = 0; // 受击无敌帧
    private double difficultyMultiplier = 1.0;
    private int baseFireCount = 1;

    // Buff 系统
    private int scatterBuffTimer = 0;
    private int dmgBuffTimer = 0;
    private int giantBuffTimer = 0;
    private static final int BUFF_DURATION = 75;
    private boolean hasScatterBuff = false;
    private boolean hasDmgBuff = false;
    private boolean hasGiantBuff = false;

    private Random random = new Random();
    private long lastFireTime = 0, lastGateSpawnTime = 0, lastHordeSpawnTime = 0;
    private GraphicsContext gc;

    //图片
    public static Image playerPlaneImage;
    public static Image background;
    private int engineFireTimer = 0;
    // 新增：控制喷火闪烁的显示状态（亮/暗切换）
    private boolean isEngineFireBright = false;

    private Image roadBgImage;
    private double bgY1 = 0;   // 第一张背景图的Y坐标
    private double bgY2 = 0;   // 第二张背景图的Y坐标（循环拼接用）
    private double bgSpeed = 5; // 背景滚动速度（数值越大，倒退越快）

    private Image enemyImage1;
    private Image enemyImage2;

    private Image bulletGiant;
    private Image bulletNormal;
    private Image bulletScatter;
    private Image bulletDamage;
    private Image bossImage;
    private Image chestImage;

    private AudioClip shootSound;
    private MediaPlayer bgmPlayer;
    private Image bossBulletImage;

    // --- 内部类：玩家子弹 ---
    class MobUnit {
        double x, y, vx, vy, damage, size;
        boolean hasPassedGate = false;
        MobUnit(double x, double y, double vx, double vy, double dmg, double sz) {
            this.x = x; this.y = y; this.vx = vx; this.vy = vy; this.damage = dmg; this.size = sz;
        }
        void update() {
            x += vx; y += vy;
            if (x < 0 || x > WIDTH - 10) vx = -vx;
        }
        void draw(GraphicsContext gc, ThunderFighter game) {
            Image useBulletImage = null;
            Color laserColor; // 定义激光颜色

            // 1. 根据Buff状态选择 图片 和 对应的激光颜色
            if (game.giantBuffTimer > 0) {
                useBulletImage = game.bulletGiant;
                laserColor = Color.rgb(255, 215, 0);
            } else if (game.scatterBuffTimer > 0) {
                useBulletImage = game.bulletScatter;
                laserColor = Color.rgb(180, 0, 255);
            } else if (game.dmgBuffTimer > 0) {
                useBulletImage = game.bulletDamage;
                laserColor = Color.rgb(0, 190, 255);
            } else {
                useBulletImage = game.bulletNormal;
                laserColor = Color.rgb(255, 50, 50);
            }

            double drawSize = this.size;

            // --- 视觉特效部分 ---

            // 2. 绘制外层光晕 (Glow) - 模拟激光的辉光
            // 保存当前画笔状态
            gc.save();
            // 设置透明度，让光晕看起来柔和
            gc.setGlobalAlpha(0.4);
            gc.setFill(laserColor);
            // 绘制一个比子弹稍大的圆形背景
            double glowSize = drawSize + 10;
            gc.fillOval(this.x - glowSize/2, this.y - glowSize/2, glowSize, glowSize);
            // 恢复画笔状态（避免影响后续绘制）
            gc.restore();

            // 3. 绘制子弹图片
            if (useBulletImage != null && !useBulletImage.isError()) {
                gc.drawImage(useBulletImage,
                        this.x - drawSize/2,
                        this.y - drawSize/2,
                        drawSize,
                        drawSize);
            } else {
                // 图片加载失败的备用绘制
                gc.setFill(laserColor);
                gc.fillOval(this.x, this.y, drawSize, drawSize);
            }

            // 4. 绘制核心描边 (Stroke) - 模拟高亮边缘
            gc.setStroke(laserColor.brighter()); // 使用更亮的颜色做描边
            gc.setLineWidth(1); // 描边宽度
            // 紧贴图片边缘画一个圈
            gc.strokeOval(this.x - drawSize/2, this.y - drawSize/2, drawSize, drawSize);
        }

    }

    // --- 内部类：BOSS 敌方弹药 ---
    class BossProjectile {
        // 【核心修改 1】 将子弹大小从 15 增加到 35
        double x, y, vx, vy, size = 35;
        BossProjectile(double x, double y, double vx, double vy) {
            this.x = x; this.y = y; this.vx = vx; this.vy = vy;
        }
        void update() { x += vx; y += vy; }
        void draw(GraphicsContext gc) {
            // 1. 检查图片是否可用
            if (bossBulletImage != null && !bossBulletImage.isError()) {
                // 如果图片加载成功，就画图片
                // x, y 是位置，size, size 是宽高
                gc.drawImage(bossBulletImage, x, y, size, size);
            } else {
                // 2. 【备用方案】如果图片没找到，还是画原来的紫色圆圈
                // (这部分是你原来的代码，作为后备)
                gc.setFill(Color.web("#8A2BE2")); // 深紫色内芯
                gc.fillOval(x, y, size, size);
                gc.setStroke(Color.web("#EE82EE")); // 亮紫色外光圈
                gc.setLineWidth(3);
                gc.strokeOval(x, y, size, size);
            }
        }
    }

    // --- 内部类：敌人/BOSS ---
    class EnemyUnit {
        double x, y, size, hp, maxHp;
        boolean isBoss;
        int attackCooldown = 0;
        int roarTimer = 0;
        private int enemyType;

        EnemyUnit(double x, double y, boolean isBoss) {
            this.x = x; this.y = y; this.isBoss = isBoss;
            if (!isBoss) {
                this.enemyType = random.nextInt(2);
                this.size = 65; // 与图片绘制尺寸65一致
                double baseHp = 8; // 基础血量，避免初期一枪秒杀（可调整：5-10为宜）
                double scoreBonus = score / 60.0; // 得分加成，控制后期血量增长速度
                this.maxHp = (1.2 + (score / 150.0)) * difficultyMultiplier;
            } else {
                this.size = 180;
                this.maxHp = 2500 * difficultyMultiplier;
            }
            this.hp = maxHp;
        }
        void update() {
            if (isBoss) {
                x += Math.sin(System.currentTimeMillis() / 1200.0) * 1.2;
                if (y < 70) y += 0.4;
                if (x < 0) x = 0;
                if (x > WIDTH - size) x = WIDTH - size;

                // --- BOSS 攻击逻辑 ---
                roarTimer++;
                attackCooldown++;

                // 1. “咆哮”震开子弹
                if (roarTimer > 180) {
                    roarTimer = 0;
                    pushBackBullets();
                }
                // 2. 发射追踪子弹 (频率稍微降低一点点，因为现在是双发)
                if (attackCooldown > 100) {
                    attackCooldown = 0;
                    double targetX = cannonX;
                    double bossBottomY = y + size;
                    double bulletSpeedY = 5.0; // 稍微加快一点速度

                    // 【核心修改 2】 双发齐射逻辑
                    // 左侧子弹发射点
                    double originX1 = x + 30;
                    // 计算左侧子弹飞向玩家的 X 向量
                    double dx1 = (targetX - originX1) / (HEIGHT / bulletSpeedY * 0.8);

                    // 右侧子弹发射点
                    double originX2 = x + size - 30;
                    // 计算右侧子弹飞向玩家的 X 向量
                    double dx2 = (targetX - originX2) / (HEIGHT / bulletSpeedY * 0.8);

                    // 同时添加两颗子弹
                    bossProjectiles.add(new BossProjectile(originX1, bossBottomY, dx1, bulletSpeedY));
                    bossProjectiles.add(new BossProjectile(originX2, bossBottomY, dx2, bulletSpeedY));
                }
            } else {
                y += 0.5;
            }
        }

        private void pushBackBullets() {
            gc.setStroke(Color.RED);
            gc.setLineWidth(5);
            gc.strokeOval(x - 50, y - 50, size + 100, size + 100);

            for (MobUnit u : playerMob) {
                double dx = u.x - (x + size/2);
                double dy = u.y - (y + size/2);
                double dist = Math.sqrt(dx*dx + dy*dy);
                if (dist < 250) {
                    u.vx = (dx / dist) * 8;
                    u.vy = Math.abs(u.vy);
                }
            }
        }

        void draw(GraphicsContext gc) {
            double hpRatio = Math.max(0, hp / maxHp);
            if (isBoss) {
                if (bossImage != null && !bossImage.isError()) {
                    // 保持原本的 size (180)，绘制图片
                    gc.drawImage(bossImage, x, y, size, size);
                } else {
                    // 图片没找到时的备用方案：画原来的紫色方块
                    gc.setFill(Color.DARKSLATEBLUE);
                    gc.fillRect(x, y, size, size);
                }

                // 2. 处理“咆哮”特效 (Roar)
                // 当 Boss 准备放大招（震开子弹）时，在图片周围闪烁红光
                if (roarTimer > 120) {
                    gc.setStroke(Color.RED);
                    gc.setLineWidth(3);
                    // 稍微把框画大一点，包住图片
                    gc.strokeRect(x - 5, y - 5, size + 10, size + 10);
                }

                // 3. (可选) 移除原本的金色边框
                // 原代码里有 gc.strokeRect(x, y, size, size);
                // 通常贴图不需要方框描边，所以这里我把它去掉了，只保留血条即可。
            } else {
                // 非BOSS敌机：随机选择两张图片绘制
                boolean isImage1Valid = enemyImage1 != null && !enemyImage1.isError();
                boolean isImage2Valid = enemyImage2 != null && !enemyImage2.isError();

                if (isImage1Valid && isImage2Valid) {
                    // 根据随机生成的enemyType选择图片
                    if (enemyType == 0) {
                        // 绘制第一张敌机图片
                        gc.drawImage(enemyImage1, x, y, 65, 65);
                    } else {
                        // 绘制第二张敌机图片
                        gc.drawImage(enemyImage2, x, y, 65, 65);
                    }
                } else {
                    // 图片加载失败时，备用纯色绘制（避免游戏异常）
                    gc.setFill(Color.color(1.0, 0.2 * hpRatio, 0.2 * hpRatio));
                    gc.fillRect(x, y, size, size);
                }
            }
            double bloodBarWidth = size * 0.7; // 血条宽度为敌机尺寸的70%（可调整：0.5~0.8为宜）
            double bloodBarX = x + (size - bloodBarWidth) / 2; // 血条水平居中（视觉更协调）
            // 绘制灰色背景血条
            gc.setFill(Color.GRAY);
            gc.fillRect(bloodBarX, y - 8, bloodBarWidth, 5); // 用bloodBarWidth代替size，缩短血条
            // 绘制绿色剩余血条
            gc.setFill(Color.LIME);
            gc.fillRect(bloodBarX, y - 8, bloodBarWidth * hpRatio, 5);
        }
    }

    // --- 内部类：宝箱 ---
    // --- 内部类：宝箱 ---
    class Chest {
        // 【修改 1】调整 size：原来的 40 有点小，看不清细节，建议改成 60 或更大
        double x, y, size = 80, hp = 5;

        Chest(double x, double y) { this.x = x; this.y = y; }
        void update() { y += 1.5; }

        // 【修改 2】完全替换 draw 方法
        void draw(GraphicsContext gc) {
            // 1. 绘制图片
            if (chestImage != null && !chestImage.isError()) {
                // 如果图片加载成功，就画图片
                gc.drawImage(chestImage, x, y, size, size);
            } else {
                // 如果图片没找到，还是画原来的金色方块做备用
                gc.setFill(Color.GOLD);
                gc.fillRect(x, y, size, size);
            }

            // 2. 绘制血量文字 (调整了一下位置，让它显示在宝箱下方，不挡住图片)
            gc.setFill(Color.WHITE);
            gc.setFont(Font.font("Arial", FontWeight.BOLD, 14)); // 字体稍微调大一点
            // 让文字大概居中显示在宝箱底部下方
            gc.fillText("HP:" + (int)hp, x + size/2 - 15, y + size + 15);
        }
    }

    // --- 内部类：进化门 ---
    class Gate {
        double x, y, w, h;
        String op;
        boolean isPurple;
        int currentCharge = 0;
        int maxCharge;

        Gate(double x, double y, double w, String op, boolean isPurple, int maxCharge) {
            this.x = x; this.y = y; this.w = w; this.h = 60;
            this.op = op;
            this.isPurple = isPurple;
            this.maxCharge = maxCharge;
        }
        void update() { y += 2.2; }
        void draw(GraphicsContext gc) {
            gc.setFill(isPurple ? Color.rgb(180, 50, 255, 0.7) : Color.rgb(0, 80, 200, 0.6));
            gc.fillRect(x, y, w, h);
            gc.setStroke(Color.WHITE);
            gc.strokeRect(x, y, w, h);
            gc.setFill(Color.WHITE);
            gc.setFont(Font.font("Arial", FontWeight.BOLD, 18));
            String display = isPurple ? op + ": " + currentCharge + "/" + maxCharge : "ATK x 2";
            gc.fillText(display, x + w / 2 - 45, y + h / 2 + 7);
        }
    }

    @Override
    public void start(Stage stage) {
        Pane root = new Pane();
        Canvas canvas = new Canvas(WIDTH, HEIGHT);
        gc = canvas.getGraphicsContext2D();
        root.getChildren().add(canvas);

        Scene scene = new Scene(root, WIDTH, HEIGHT);
        scene.setOnMouseMoved(e -> cannonX = e.getX());

        try {
            playerPlaneImage = new Image("plane.png");
            enemyImage1 = new Image("enemy1.png");
            enemyImage2 = new Image("enemy2.png");
            // 新增：加载俯视道路背景图
            roadBgImage = new Image("Road.png");
            bulletGiant = new Image("bulletGiant.png");   // 对应第一个图（巨型）
            bulletNormal = new Image("bulletNormal.png"); // 对应第二个图（普通）
            bulletScatter = new Image("bulletScatter.png"); // 对应第三个图（散射）
            bulletDamage = new Image("bulletDamage.png"); // 对应第四个图（伤害）
            bossImage = new Image("boss.png");
            chestImage = new Image("chest.png");
            bossBulletImage = new Image("bossBullet.png");
            String soundPath = new File("Shoot.wav").toURI().toString();
            shootSound = new AudioClip(soundPath);
            // 设置音量 (0.0 - 1.0)
            shootSound.setVolume(0.2);

            System.out.println("音效加载成功！");

            // 1. 找到文件路径 (假设文件名叫 bgm.mp3)
            String bgmPath = new File("bgm.wav").toURI().toString();

            // 2. 创建 Media 对象 (相当于把文件加载进来)
            Media bgmMedia = new Media(bgmPath);

            // 3. 创建播放器
            bgmPlayer = new MediaPlayer(bgmMedia);

            // 4. 设置循环播放 (关键！否则播一遍就停了)
            bgmPlayer.setCycleCount(MediaPlayer.INDEFINITE);

            // 5. 设置音量 (背景音乐不要太吵，建议 0.3 - 0.5，比音效小一点)
            bgmPlayer.setVolume(0.5);

            // 6. 开始播放
            bgmPlayer.play();

            System.out.println("BGM 加载并播放成功！");
            // 初始化两张背景图位置：第一张在顶部(bgY1=0)，第二张在第一张上方(bgY2=-背景图高度)
            if (enemyImage1.isError() || enemyImage2.isError()) {
                System.out.println("敌机图片加载失败："
                        + (enemyImage1.isError() ? "enemy1加载异常：" + enemyImage1.getException().getMessage() : "")
                        + (enemyImage2.isError() ? "enemy2加载异常：" + enemyImage2.getException().getMessage() : ""));
            } else {
                System.out.println("两张敌机图片加载成功！");
            }
            if(roadBgImage != null && !roadBgImage.isError()){
                bgY2 = -roadBgImage.getHeight();
            }
        } catch (Exception e) {
            System.out.println("图片加载异常：" + e.getMessage());
        }


        new AnimationTimer() {
            @Override
            public void handle(long now) {
                gc.setFill(Color.BLACK); // 清屏底色，和你原有一致就行
                gc.fillRect(0, 0, WIDTH, HEIGHT);

                // ========== 新增：背景道路滚动核心逻辑（清屏后立刻绘制） ==========
                if(roadBgImage != null && !roadBgImage.isError()){
                    // 1. 绘制两张道路背景图（循环拼接，无缝滚动）
                    gc.drawImage(roadBgImage, 0, bgY1, WIDTH, roadBgImage.getHeight());
                    gc.drawImage(roadBgImage, 0, bgY2, WIDTH, roadBgImage.getHeight());

                    // 2. 背景向下滚动（视觉上道路往后退）
                    bgY1 += bgSpeed;
                    bgY2 += bgSpeed;

                    // 3. 循环重置：图片滚出屏幕底部，就拉回顶部，实现无限滚动
                    double bgHeight = roadBgImage.getHeight();
                    if(bgY1 >= HEIGHT){
                        bgY1 = -bgHeight;
                    }
                    if(bgY2 >= HEIGHT){
                        bgY2 = -bgHeight;
                    }
                }
                drawBackground();

                if (isGameOver || isVictory) { drawResult(); return; }
                handleFiring(now);
                handleGates(now);
                handleChests(now);
                handlePlayerUnits();
                handleEnemyHorde(now);
                handleBossProjectiles(); // 处理 BOSS 子弹逻辑
                checkCombatAndGameOver();
                drawUI();
                difficultyMultiplier = 1.0 + (score / 4000.0);
                if (score >= BOSS_TRIGGER_SCORE && !bossSpawned) spawnBoss();
                if (invincibleTimer > 0) invincibleTimer--;
                engineFireTimer++;
                // 每8帧切换一次喷火状态（数值越大，闪烁越慢，可调整为6/10等）
                if (engineFireTimer % 8 == 0) {
                    isEngineFireBright = !isEngineFireBright;
                    engineFireTimer = 0; // 重置定时器
                }
            }
        }.start();

        stage.setScene(scene);
        stage.setTitle("人群进化：BOSS 双重打击版");
        stage.show();
    }

    private void spawnBoss() {
        bossSpawned = true;
        enemyMob.clear();
        finalBoss = new EnemyUnit(WIDTH / 2.0 - 90, -200, true);
        enemyMob.add(finalBoss);
    }

    private void drawBackground() {
        gc.setStroke(Color.rgb(0, 200, 255, 0.3));
        gc.strokeLine(0, TOP_CLEANUP_LINE, WIDTH, TOP_CLEANUP_LINE);
        gc.setStroke(Color.rgb(150, 0, 0, 0.5));
        gc.strokeLine(0, DEAD_LINE, WIDTH, DEAD_LINE);
    }

    private void handleFiring(long now) {
        // 新增：标记是否正在发射（用于控制闪光特效显示）
        boolean isFiring = false;

        if (now - lastFireTime > 240 * 1_000_000L) {
            // 原有子弹发射逻辑（完全不变，复制保留即可）
            if (shootSound != null) {
                shootSound.play();
            }
            double currentDmg = (dmgBuffTimer > 0) ? 2.0 : 1.0;
            double currentSize = (giantBuffTimer > 0) ? 18.0 : 15.0;

            for (int i = 0; i < baseFireCount; i++) {
                double xOffset = (i - (baseFireCount - 1) / 2.0) * 12;
                if (scatterBuffTimer > 0) {
                    for (int j = -1; j <= 1; j++) {
                        playerMob.add(new MobUnit(cannonX + xOffset, HEIGHT - 60, j * 2.2, -10.5, currentDmg, currentSize));
                    }
                } else {
                    playerMob.add(new MobUnit(cannonX + xOffset, HEIGHT - 60, 0, -9.0, currentDmg, currentSize));
                }
            }

            if (scatterBuffTimer > 0) {
                scatterBuffTimer--;
                if(scatterBuffTimer == 0) hasScatterBuff = false;
            }
            if (dmgBuffTimer > 0) {
                dmgBuffTimer--;
                if(dmgBuffTimer == 0) hasDmgBuff = false;
            }
            if (giantBuffTimer > 0) {
                giantBuffTimer--;
                if(giantBuffTimer == 0) hasGiantBuff = false;
            }
            lastFireTime = now;

            // 新增：标记当前处于发射状态，触发闪光特效
            isFiring = true;
        }

        // 替换原有炮台绘制：绘制飞机贴图（保留受击闪烁效果）
        if (invincibleTimer % 4 == 0) {
            if (playerPlaneImage != null && !playerPlaneImage.isError()) {
                // 绘制飞机贴图：保持坐标与原炮台一致，确保碰撞检测精准
                // cannonX - 20：让贴图居中跟随鼠标；HEIGHT - 60：保持原炮台位置
                // 40, 40：贴图尺寸，可根据你的图片实际大小调整（建议与图片尺寸一致）
                gc.drawImage(
                        playerPlaneImage,
                        cannonX -50,  // X坐标：对应宽度60，取一半（30），保证飞机居中跟随鼠标
                        HEIGHT - 120,   // Y坐标：适当向上调整（减小数值），避免飞机超出屏幕下方（可选）
                        100,            // 宽度：放大为60（可改为50、70、80等，根据需求调整）
                        100             // 高度：与宽度保持一致，避免飞机变形
                );

                double engineX = cannonX - 2; // 引擎横向中心（飞机尾部中间）
                double engineY = (HEIGHT - 100) + 60; // 引擎纵向位置（飞机屁股下方，即尾部）

                // 根据闪烁状态切换火焰透明度/颜色，实现闪烁效果
                if (isEngineFireBright) {
                    // 亮态火焰（三层，更鲜艳）
                    // 1. 外层淡红色光晕（最淡，模拟热气流）
                    gc.setFill(Color.rgb(255, 105, 97, 0.4));
                    gc.fillOval(engineX - 17, engineY, 40, 50);
                    // 2. 中层橙黄色火焰（核心火焰）
                    gc.setFill(Color.rgb(255, 165, 0, 0.7));
                    gc.fillOval(engineX - 12, engineY + 5, 30, 45);
                    // 3. 内层亮黄色火焰（最亮，模拟火焰核心）
                    gc.setFill(Color.rgb(255, 255, 0, 0.9));
                    gc.fillOval(engineX - 8, engineY + 10, 20, 40);
                } else {
                    // 暗态火焰（三层，更淡，实现闪烁效果）
                    // 1. 外层淡红色光晕
                    gc.setFill(Color.rgb(255, 90, 97, 0.2));
                    gc.fillOval(engineX - 17, engineY, 40, 40);
                    // 2. 中层橙黄色火焰
                    gc.setFill(Color.rgb(255, 140, 0, 0.4));
                    gc.fillOval(engineX - 12, engineY + 5, 30, 35);
                    // 3. 内层亮黄色火焰
                    gc.setFill(Color.rgb(255, 220, 0, 0.6));
                    gc.fillOval(engineX - 8, engineY + 10, 20, 30);
                }

                // 左右两侧弹舱闪光特效（前移到弹舱前方，替换原有代码）
                if (isFiring) {
                    // ========== 左侧弹舱闪光（前移版，三组层级） ==========
                    // 1. 左弹舱内层亮白色闪光（核心火光，纵向数值从70改为50，前移）
                    gc.setFill(Color.WHITE);
                    gc.fillOval(
                            (cannonX - 50) + 22,  // 横向位置不变，保持左弹舱横向对齐   小左大右
                            (HEIGHT - 100) + 6,  // 纵向数值减小（70→50），特效向上/向前移动
                            4,                    // 闪光宽度（保持不变，小巧适配）
                            30         // 闪光高度（保持不变）
                    );

                    // 2. 左弹舱中层黄色闪光（纵向同步前移）
                    gc.setFill(Color.rgb(255, 255, 0, 0.8));
                    gc.fillOval(
                            (cannonX - 50) + 20,  // 横向微调不变
                            (HEIGHT - 100) + 4,  // 纵向数值减小（68→48），与内层同步前移
                            8,                    // 闪光宽度不变
                            32                  // 闪光高度不变
                    );

                    // 3. 左弹舱外层蓝色闪光（纵向同步前移）
                    gc.setFill(Color.rgb(20, 187, 225, 0.9));
                    gc.fillOval(
                            (cannonX - 50) + 20,  // 横向微调不变
                            (HEIGHT - 100) + 2,  // 纵向数值减小（66→46），与内层同步前移
                            10,                    // 闪光宽度不变
                            40                     // 闪光高度不变
                    );



                    // ========== 右侧弹舱闪光（与左侧对称，同步前移） ==========
                    // 1. 右弹舱内层亮白色闪光（纵向数值同步改为50）
                    gc.setFill(Color.WHITE);
                    gc.fillOval(
                            (cannonX - 50) + 70,  // 横向位置不变，保持右弹舱横向对齐
                            (HEIGHT - 100) + 6,  // 纵向数值减小（70→50），前移到弹舱前方
                            4,                    // 闪光宽度不变
                            30                   // 闪光高度不变
                    );

                    // 2. 右弹舱中层黄色闪光（纵向同步改为48）
                    gc.setFill(Color.rgb(255, 255, 0, 0.8));
                    gc.fillOval(
                            (cannonX - 50) + 70,  // 横向微调不变
                            (HEIGHT - 100) + 4,  // 纵向数值减小（68→48），同步前移
                            8,                    // 闪光宽度不变
                            32                     // 闪光高度不变
                    );

                    // 3. 右弹舱外层蓝色闪光（纵向同步改为46）
                    gc.setFill(Color.rgb(20, 187, 225, 0.9));
                    gc.fillOval(
                            (cannonX - 50) + 70,  // 横向微调不变
                            (HEIGHT - 100) + 2,  // 纵向数值减小（66→46），同步前移
                            10,                    // 闪光宽度不变
                            40                   // 闪光高度不变
                    );

                    // 可选：细描边（同步前移，按需保留）
                    gc.setStroke(Color.WHITE);
                    gc.setLineWidth(1);
                    gc.strokeOval((cannonX - 50) + 24, (HEIGHT - 100) + 25, 5, 10);
                    gc.strokeOval((cannonX - 50) + 70, (HEIGHT - 100) + 25, 5, 10);
                }
                // ==============================================================

            } else {
                // 图片加载失败时，备用绘制原炮台（避免游戏异常）
                gc.setFill(scatterBuffTimer > 0 ? Color.GOLD : (dmgBuffTimer > 0 ? Color.RED : Color.DODGERBLUE));
                gc.fillRoundRect(cannonX - 20, HEIGHT - 60, 40, 40, 10, 10);
            }
        }
    }

    private void handleBossProjectiles() {
        Iterator<BossProjectile> it = bossProjectiles.iterator();
        while (it.hasNext()) {
            BossProjectile p = it.next();
            p.update();
            p.draw(gc);

            // 碰撞玩家炮台判定 (因为子弹变大了，稍微增加一点碰撞判定的宽容度)
            if (invincibleTimer <= 0 && p.y > HEIGHT - 75 && Math.abs(p.x + p.size/2 - cannonX) < 30) {
                playerHP--;
                invincibleTimer = 60; // 1秒无敌
                it.remove();
                if (playerHP <= 0) isGameOver = true;
            } else if (p.y > HEIGHT) {
                it.remove();
            }
        }
    }

    private void handleGates(long now) {
        if (now - lastGateSpawnTime > 6000 * 1_000_000L) {
            int currentReq = Math.min(35, 10 + (score / 120));
            boolean purpleOnLeft = random.nextBoolean();
            String purpleMode = random.nextBoolean() ? "BURST" : "GIANT";
            if (purpleOnLeft) {
                gates.add(new Gate(0, -100, WIDTH / 2.0, purpleMode, true, currentReq));
                gates.add(new Gate(WIDTH / 2.0, -100, WIDTH / 2.0, "ATK", false, 0));
            } else {
                gates.add(new Gate(0, -100, WIDTH / 2.0, "ATK", false, 0));
                gates.add(new Gate(WIDTH / 2.0, -100, WIDTH / 2.0, purpleMode, true, currentReq));
            }
            lastGateSpawnTime = now;
        }
        gates.removeIf(g -> { g.update(); g.draw(gc); return g.y > HEIGHT; });
    }

    private void handleChests(long now) {
        if (random.nextInt(850) == 0) chests.add(new Chest(random.nextDouble() * (WIDTH - 40), -50));
        chests.removeIf(c -> { c.update(); c.draw(gc); return c.y > HEIGHT; });
    }

    private void handlePlayerUnits() {
        List<MobUnit> newUnits = new ArrayList<>();
        Iterator<MobUnit> it = playerMob.iterator();
        while (it.hasNext()) {
            MobUnit u = it.next();
            u.update();
            u.draw(gc, this);

            if (u.y < TOP_CLEANUP_LINE) { it.remove(); continue; }
            if (u.y > HEIGHT) { it.remove(); continue; }

            boolean removed = false;
            for (Gate g : gates) {
                if (u.x > g.x && u.x < g.x + g.w && u.y < g.y + g.h && u.y > g.y) {
                    if (g.isPurple) {
                        g.currentCharge++;
                        if (g.currentCharge >= g.maxCharge) {
                            if (g.op.equals("BURST")) {
                                triggerBurst(g.x + g.w/2, g.y + g.h/2, newUnits);
                                scatterBuffTimer = BUFF_DURATION;
                                hasScatterBuff = true;
                            } else {
                                giantBuffTimer = BUFF_DURATION;
                                hasGiantBuff = true;
                                scatterBuffTimer /= 2;
                                dmgBuffTimer /= 2;
                            }
                            g.y = 2000;
                        }
                        it.remove(); removed = true; break;
                    } else if (!u.hasPassedGate) {
                        u.hasPassedGate = true;
                        dmgBuffTimer = BUFF_DURATION;
                        hasDmgBuff = true;
                        scatterBuffTimer /= 2;
                        giantBuffTimer /= 2;
                    }
                }
            }
            if (!removed) {
                for (Chest c : chests) {
                    if (u.x > c.x && u.x < c.x + c.size && u.y > c.y && u.y < c.y + c.size) {
                        c.hp -= u.damage; it.remove(); removed = true;
                        if (c.hp <= 0) { baseFireCount++; c.y = 2000; }
                        break;
                    }
                }
            }
        }
        if (playerMob.size() < 600) playerMob.addAll(newUnits);
    }

    private void handleEnemyHorde(long now) {
        if (bossSpawned) { if (finalBoss != null) { finalBoss.update(); finalBoss.draw(gc); } return; }
        if (now - lastHordeSpawnTime > 2000 * 1_000_000L) {
            for (int i = 0; i < 8; i++) {
                if (random.nextInt(10) < 6) { enemyMob.add(new EnemyUnit(i * (WIDTH / 8.0) + 2, -50, false)); }
            }
            lastHordeSpawnTime = now;
        }
        enemyMob.forEach(e -> { e.update(); e.draw(gc); });
    }

    private void checkCombatAndGameOver() {
        Iterator<EnemyUnit> eIt = enemyMob.iterator();
        while (eIt.hasNext()) {
            EnemyUnit e = eIt.next();
            if (e.y + e.size > DEAD_LINE) { isGameOver = true; return; }

            Iterator<MobUnit> pIt = playerMob.iterator();
            while (pIt.hasNext()) {
                MobUnit p = pIt.next();
                if (p.x > e.x && p.x < e.x + e.size && p.y > e.y && p.y < e.y + e.size) {
                    e.hp -= p.damage; pIt.remove();
                    if (e.hp <= 0) {
                        if (e.isBoss) isVictory = true;
                        score += e.isBoss ? 1000 : 20;
                        e.hp = -100; break;
                    }
                }
            }
            if (e.hp == -100) eIt.remove();
        }
    }

    private void triggerBurst(double x, double y, List<MobUnit> newUnits) {
        for (int i = 0; i < 35; i++) {
            double angle = 240 + random.nextDouble() * 60;
            newUnits.add(new MobUnit(x, y, Math.cos(Math.toRadians(angle)) * 12, Math.sin(Math.toRadians(angle)) * 12, 1.0, 9.0));
        }
    }

    private void drawUI() {
        gc.setFill(Color.YELLOW);
        gc.setFont(Font.font("Arial", FontWeight.BOLD, 16));
        gc.fillText("兵力: " + playerMob.size(), 20, 30);
        gc.fillText("进度: " + score + " / " + BOSS_TRIGGER_SCORE, 20, 50);

        // 生命值显示
        gc.setFill(Color.RED);
        String hearts = "";
        for(int i=0; i<playerHP; i++) hearts += "❤ ";
        gc.fillText("生命值: " + hearts, 20, 75);

        int currentReq = Math.min(35, 10 + (score / 120));
        gc.setFill(Color.VIOLET);
        gc.fillText("进化门槛: " + currentReq, WIDTH - 120, 30);

        if (scatterBuffTimer > 0) { gc.setFill(Color.GOLD); gc.fillText("BUFF: 散射爆发!", 20, 100); }
        if (dmgBuffTimer > 0) { gc.setFill(Color.RED); gc.fillText("BUFF: 攻击倍增!", 20, 120); }
        if (giantBuffTimer > 0) { gc.setFill(Color.VIOLET); gc.fillText("BUFF: 巨型弹头!", 20, 140); }
    }

    private void drawResult() {
        gc.setFill(Color.rgb(0, 0, 0, 0.8));
        gc.fillRect(0, 0, WIDTH, HEIGHT);
        gc.setFont(Font.font("Arial", FontWeight.BOLD, 60));
        if (isVictory) { gc.setFill(Color.GOLD); gc.fillText("YOU WIN!", WIDTH / 2.0 - 140, HEIGHT / 2.0); }
        else { gc.setFill(Color.RED); gc.fillText("FAILED", WIDTH / 2.0 - 100, HEIGHT / 2.0); }
    }

    public static void main(String[] args) {
        System.setProperty("prism.order", "sw");
        launch(args);
    }
}
