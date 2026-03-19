package DesertBird;

import javax.imageio.ImageIO;
import javax.sound.sampled.*;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.awt.image.ImageObserver;
import java.io.*;
import java.net.URL;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

enum BirdState {
    IDLE,
    JUMP,
    FALL,
    HIT,
    DEAD_FALL
}

class SettingsManager {

    private static final String APP_FOLDER = "DesertBird";
    private static final String SETTINGS_FILE = "settings.dat";

    private static File getSettingsFile() {
        String localAppData = System.getenv("LOCALAPPDATA");
        File appDir;

        if (localAppData != null && !localAppData.isBlank()) {
            appDir = new File(localAppData, APP_FOLDER);
        } else {
            String userHome = System.getProperty("user.home");
            appDir = new File(userHome, APP_FOLDER);
        }

        if (!appDir.exists()) {
            appDir.mkdirs();
        }

        return new File(appDir, SETTINGS_FILE);
    }

    public static int loadHighScore() {
        File file = getSettingsFile();

        if (!file.exists()) {
            return 0;
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("highscore=")) {
                    return Integer.parseInt(line.split("=", 2)[1].trim());
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return 0;
    }

    public static boolean loadMuted() {
        File file = getSettingsFile();

        if (!file.exists()) {
            return false;
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("muted=")) {
                    return Boolean.parseBoolean(line.split("=", 2)[1].trim());
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return false;
    }

    public static void saveSettings(int highScore, boolean muted) {
        File file = getSettingsFile();

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
            writer.write("highscore=" + highScore);
            writer.newLine();
            writer.write("muted=" + muted);
            writer.newLine();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

class SoundManager {
    private Clip mainClip;
    private Clip gameOverClip;
    private boolean muted = false;

    private static final String SOUND_SETTINGS_FILE = "sound_settings.dat";

    public SoundManager() {
        muted = loadMutedState();
        mainClip = loadClip("main.wav");
        gameOverClip = loadClip("game_over.wav");
    }

    private Clip loadClip(String fileName) {
        try {
            InputStream rawStream = getClass().getResourceAsStream(fileName);
            if (rawStream == null) {
                return null;
            }

            BufferedInputStream bufferedStream = new BufferedInputStream(rawStream);
            AudioInputStream audioInputStream = AudioSystem.getAudioInputStream(bufferedStream);
            Clip clip = AudioSystem.getClip();
            clip.open(audioInputStream);
            return clip;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private void playFreshClip(String fileName) {
        if (muted) {
            return;
        }

        try {
            InputStream rawStream = getClass().getResourceAsStream(fileName);
            if (rawStream == null) {
                return;
            }

            BufferedInputStream bufferedStream = new BufferedInputStream(rawStream);
            AudioInputStream audioInputStream = AudioSystem.getAudioInputStream(bufferedStream);
            Clip clip = AudioSystem.getClip();
            clip.open(audioInputStream);
            clip.start();

            clip.addLineListener(event -> {
                if (event.getType() == LineEvent.Type.STOP) {
                    clip.close();
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void playMainLoop() {
        if (muted || mainClip == null) {
            return;
        }

        if (mainClip.isRunning()) {
            return;
        }

        mainClip.setFramePosition(0);
        mainClip.loop(Clip.LOOP_CONTINUOUSLY);
    }

    public void stopMain() {
        if (mainClip != null && mainClip.isRunning()) {
            mainClip.stop();
        }
    }

    public void stopGameOver() {
        if (gameOverClip != null && gameOverClip.isRunning()) {
            gameOverClip.stop();
        }
        if (gameOverClip != null) {
            gameOverClip.setFramePosition(0);
        }
    }

    public void ensureMainPlayingIfAllowed() {
        if (!muted) {
            playMainLoop();
        }
    }

    public void toggleMute() {
        muted = !muted;
        saveMutedState();

        if (muted) {
            stopMain();
            stopGameOver();
        } else {
            playMainLoop();
        }
    }

    public void toggleMuteWithoutResume() {
        muted = !muted;
        saveMutedState();

        if (muted) {
            stopMain();
            stopGameOver();
        }
    }

    public void playJump() {
        playFreshClip("jump.wav");
    }

    public void playScore() {
        playFreshClip("score.wav");
    }

    public void playHit() {
        playFreshClip("hit.wav");
    }

    public void playDead() {
        playFreshClip("dead.wav");
    }

    public void playGameOver() {
        if (muted || gameOverClip == null) {
            return;
        }

        if (gameOverClip.isRunning()) {
            gameOverClip.stop();
        }

        gameOverClip.setFramePosition(0);
        gameOverClip.start();
    }

    public boolean isMuted() {
        return muted;
    }

    private boolean loadMutedState() {
        File file = new File(SOUND_SETTINGS_FILE);

        if (!file.exists()) {
            return false;
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line = reader.readLine();
            if (line != null) {
                return Boolean.parseBoolean(line.trim());
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return false;
    }

    private void saveMutedState() {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(SOUND_SETTINGS_FILE))) {
            writer.write(String.valueOf(muted));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

abstract class GameObject {
    protected int x, y;
    protected int dx, dy;
    protected int width, height;
    protected Image image;

    public GameObject(int x, int y) {
        this.x = x;
        this.y = y;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public int getDx() {
        return dx;
    }

    public int getDy() {
        return dy;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public Image getImage() {
        return image;
    }

    public void setX(int x) {
        this.x = x;
    }

    public void setY(int y) {
        this.y = y;
    }

    public void setDx(int dx) {
        this.dx = dx;
    }

    public void setDy(int dy) {
        this.dy = dy;
    }

    public void setWidth(int width) {
        this.width = width;
    }

    public void setHeight(int height) {
        this.height = height;
    }

    public void setImage(Image image) {
        this.image = image;
    }

    public abstract void tick();
    public abstract void render(Graphics2D g, ImageObserver obs);
}

class Bird extends GameObject {
    @SuppressWarnings("unused")
    private static final int SOURCE_FRAME_SIZE = 128;

    private static final int DRAW_SIZE = 64;

    private static final int IDLE_FRAME_COUNT = 6;

    private static final int FALL_LIMIT = 10;

    private double velocityY;

    private final double gravity = 0.6;
    private final double jumpStrength = -8.5;

    private BirdState state = BirdState.IDLE;

    private BufferedImage[] idleFrames;
    private Image jumpFrame;
    private Image fallFrame;
    private Image hitFrame;
    private Image deadFrame;

    private int frameIndex = 0;
    private int animationTick = 0;
    private int stateTimer = 0;

    private boolean stunned = false;

    public Bird(int x, int y) {
        super(x, y);

        loadAnimations();

        this.image = idleFrames[0] != null ? idleFrames[0] : jumpFrame;
        this.width = DRAW_SIZE;
        this.height = DRAW_SIZE;
        this.velocityY = 0;
    }

    private void loadAnimations() {
        idleFrames = loadIdleSheet("eagle_idle_sheet.png");
        jumpFrame = loadSingleImage("eagle_jump.png");
        fallFrame = loadSingleImage("eagle_fall.png");
        hitFrame = loadSingleImage("eagle_hit.png");
        deadFrame = loadSingleImage("eagle_dead.png");
    }

    private BufferedImage[] loadIdleSheet(String fileName) {

        BufferedImage[] frames = new BufferedImage[IDLE_FRAME_COUNT];

        try {
            URL resource = getClass().getResource(fileName);
            if (resource != null) {

                BufferedImage sheet = ImageIO.read(resource);

                if (sheet != null) {

                    int baseFrameCount = 4;
                    int frameWidth = sheet.getWidth() / baseFrameCount;
                    int frameHeight = sheet.getHeight();

                    BufferedImage[] baseFrames = new BufferedImage[baseFrameCount];

                    for (int i = 0; i < baseFrameCount; i++) {
                        baseFrames[i] = sheet.getSubimage(i * frameWidth, 0, frameWidth, frameHeight);
                    }

                    // ping-pong
                    frames[0] = baseFrames[0];
                    frames[1] = baseFrames[1];
                    frames[2] = baseFrames[2];
                    frames[3] = baseFrames[3];
                    frames[4] = baseFrames[2];
                    frames[5] = baseFrames[1];
                }
            }

        } catch (IOException ignored) {
        }

        return frames;
    }
    
    public void tickIdleOnly() {
        state = BirdState.IDLE;
        velocityY = 0;
        updateAnimation();
    }

    private Image loadSingleImage(String fileName) {
        URL resource = getClass().getResource(fileName);
        if (resource == null) {
            return null;
        }

        ImageIcon icon = new ImageIcon(resource);
        if (icon.getIconWidth() > 0 && icon.getIconHeight() > 0) {
            return icon.getImage();
        }
        return null;
    }

    @Override
    public void tick() {
        if (state == BirdState.HIT) {
            tickHit();
            updateAnimation();
            return;
        }

        if (state == BirdState.DEAD_FALL) {
            tickDeadFall();
            updateAnimation();
            return;
        }

        velocityY += gravity;
        if (velocityY > FALL_LIMIT) {
            velocityY = FALL_LIMIT;
        }

        y += (int) Math.round(velocityY);

        updateStateByVelocity();
        updateAnimation();
    }

    private void tickHit() {
        x -= 3;
        y -= 2;

        stateTimer--;
        if (stateTimer <= 0) {
            state = BirdState.DEAD_FALL;
            velocityY = 2;
            frameIndex = 0;
            animationTick = 0;
        }
    }

    private void tickDeadFall() {
        velocityY += gravity;
        if (velocityY > 12) {
            velocityY = 12;
        }
        y += (int) Math.round(velocityY);
    }

    public void jump() {
        if (state == BirdState.HIT || state == BirdState.DEAD_FALL) {
            return;
        }

        velocityY = jumpStrength;
        state = BirdState.JUMP;
        frameIndex = 0;
        animationTick = 0;
    }

    public void hitRock() {
        if (stunned) {
            return;
        }

        stunned = true;
        state = BirdState.HIT;
        stateTimer = 14;
        velocityY = -1.5;
        frameIndex = 0;
        animationTick = 0;
    }

    private void updateStateByVelocity() {
        if (state == BirdState.HIT || state == BirdState.DEAD_FALL) {
            return;
        }

        if (velocityY < -1) {
            state = BirdState.JUMP;
        } else if (velocityY > 1.5) {
            state = BirdState.FALL;
        } else {
            state = BirdState.IDLE;
        }
    }

    private void updateAnimation() {
        animationTick++;

        switch (state) {
            case IDLE:
                if (animationTick >= 6) {
                    animationTick = 0;
                    frameIndex = (frameIndex + 1) % IDLE_FRAME_COUNT;
                }
                image = idleFrames[frameIndex] != null ? idleFrames[frameIndex] : null;
                break;

            case JUMP:
                image = jumpFrame != null ? jumpFrame : idleFrames[0];
                break;

            case FALL:
                image = fallFrame != null ? fallFrame : idleFrames[2];
                break;

            case HIT:
                image = hitFrame != null ? hitFrame : fallFrame;
                break;

            case DEAD_FALL:
                image = deadFrame != null ? deadFrame : hitFrame;
                break;
        }
    }

    public BirdState getState() {
        return state;
    }

    public void setVelocityY(double velocityY) {
        this.velocityY = velocityY;
    }

    public double getVelocityY() {
        return velocityY;
    }

    @Override
    public void render(Graphics2D g, ImageObserver obs) {
        double angle;

        if (state == BirdState.HIT) {
            angle = Math.toRadians(-20);
        } else if (state == BirdState.DEAD_FALL) {
            angle = Math.toRadians(75);
        } else if (state == BirdState.IDLE) {
            angle = Math.toRadians(-5);
        } else {
            angle = Math.toRadians(Math.max(-30, Math.min(70, velocityY * 5)));
        }

        AffineTransform old = g.getTransform();
        g.rotate(angle, x + width / 2.0, y + height / 2.0);

        if (image != null) {
            g.drawImage(image, x, y, width, height, obs);
        } else {
            drawFallbackBird(g);
        }

        g.setTransform(old);
    }

    private void drawFallbackBird(Graphics2D g) {
        g.setColor(Color.YELLOW);
        g.fillOval(x + 8, y + 14, width - 16, height - 28);
        g.setColor(Color.BLACK);
        g.drawOval(x + 8, y + 14, width - 16, height - 28);
    }

    public Rectangle getBounds() {
        return new Rectangle(x + 10, y + 10, width - 20, height - 20);
    }
}

class Tube extends GameObject {

    public static final int TUBE_WIDTH = 96;

    private static final int TOP_CAP_HEIGHT = 48;
    private static final int BOTTOM_CAP_HEIGHT = 40;

    private static final int BODY_OVERLAP = 12;

    private static Image rockTopCap;
    private static Image rockBodyTile;
    private static Image rockBottomCap;

    private final boolean topTube;

    public Tube(int x, int y, int width, int height, int speed, boolean topTube) {
        super(x, y);
        this.width = width;
        this.height = Math.max(0, height);
        this.dx = speed;
        this.topTube = topTube;

        loadRockSprites();
    }

    private static void loadRockSprites() {
        if (rockTopCap == null) {
            rockTopCap = loadSprite("rock_top_cap.png");
        }
        if (rockBodyTile == null) {
            rockBodyTile = loadSprite("rock_body_tile.png");
        }
        if (rockBottomCap == null) {
            rockBottomCap = loadSprite("rock_bottom_cap.png");
        }
    }

    private static Image loadSprite(String fileName) {
        URL resource = Tube.class.getResource(fileName);
        if (resource == null) {
            return null;
        }

        ImageIcon icon = new ImageIcon(resource);
        if (icon.getIconWidth() > 0 && icon.getIconHeight() > 0) {
            return icon.getImage();
        }
        return null;
    }

    public void tick(int speed) {
        this.x -= speed;
    }

    @Override
    public void tick() {
        this.x -= dx;
    }

    @Override
    public void render(Graphics2D g, ImageObserver obs) {
        if (height <= 0) {
            return;
        }

        if (topTube) {
            renderTopRock(g, obs);
        } else {
            renderBottomRock(g, obs);
        }
    }

    private void renderTopRock(Graphics2D g, ImageObserver obs) {

        if (rockBodyTile != null) {
            int capHeight = Math.min(TOP_CAP_HEIGHT, height);
            int bodyHeight = Math.max(0, height - capHeight + BODY_OVERLAP);

            drawRepeatedBodyRotated180(
                    g, obs,
                    x, y,
                    width, bodyHeight,
                    rockBodyTile, BODY_OVERLAP
            );

            if (rockTopCap != null) {
                int capY = y + height - capHeight;
                g.drawImage(
                        rockTopCap,
                        x, capY, x + width, capY + capHeight,
                        rockTopCap.getWidth(null), rockTopCap.getHeight(null), 0, 0,
                        obs
                );
            }
        } else {
            drawFallbackTopRock(g);
        }
    }

    private void renderBottomRock(Graphics2D g, ImageObserver obs) {

        if (rockBodyTile != null) {
            int topCapHeight = Math.min(TOP_CAP_HEIGHT, height);
            int bottomCapHeight = Math.min(BOTTOM_CAP_HEIGHT, height);

            int bodyY = y + topCapHeight - BODY_OVERLAP;
            int bodyHeight = Math.max(0, height - topCapHeight - bottomCapHeight + BODY_OVERLAP * 2);

            drawRepeatedBody(
                    g, obs,
                    x, bodyY,
                    width, bodyHeight,
                    rockBodyTile, BODY_OVERLAP
            );

            if (rockTopCap != null) {
                g.drawImage(rockTopCap, x, y, width, topCapHeight, obs);
            }

            if (rockBottomCap != null) {
                int bottomY = y + height - bottomCapHeight;
                g.drawImage(rockBottomCap, x, bottomY, width, bottomCapHeight, obs);
            }
        } else {
            drawFallbackBottomRock(g);
        }
    }

    private void drawRepeatedBody(Graphics2D g, ImageObserver obs,
                                  int drawX, int drawY, int drawWidth, int drawHeight,
                                  Image tile, int overlap) {
        if (tile == null || drawHeight <= 0) {
            return;
        }

        int srcW = tile.getWidth(null);
        int srcH = tile.getHeight(null);

        if (srcW <= 0 || srcH <= 0) {
            return;
        }

        int step = Math.max(1, srcH - overlap);
        int currentY = drawY;
        int endY = drawY + drawHeight;

        while (currentY < endY) {
            int remaining = endY - currentY;
            int pieceHeight = Math.min(srcH, remaining);

            g.drawImage(
                    tile,
                    drawX, currentY, drawX + drawWidth, currentY + pieceHeight,
                    0, 0, srcW, pieceHeight,
                    obs
            );

            currentY += step;
        }
    }

    private void drawRepeatedBodyRotated180(Graphics2D g, ImageObserver obs,
                                            int drawX, int drawY, int drawWidth, int drawHeight,
                                            Image tile, int overlap) {
        if (tile == null || drawHeight <= 0) {
            return;
        }

        int srcW = tile.getWidth(null);
        int srcH = tile.getHeight(null);

        if (srcW <= 0 || srcH <= 0) {
            return;
        }

        int step = Math.max(1, srcH - overlap);
        int currentY = drawY;
        int endY = drawY + drawHeight;

        while (currentY < endY) {
            int remaining = endY - currentY;
            int pieceHeight = Math.min(srcH, remaining);

            g.drawImage(
                    tile,
                    drawX, currentY, drawX + drawWidth, currentY + pieceHeight,
                    srcW, srcH, 0, srcH - pieceHeight,
                    obs
            );

            currentY += step;
        }
    }

    private void drawFallbackTopRock(Graphics2D g) {
        g.setColor(new Color(205, 170, 95));
        g.fillRect(x, y, width, height);
        g.setColor(new Color(140, 100, 55));
        g.drawRect(x, y, width - 1, height - 1);
    }

    private void drawFallbackBottomRock(Graphics2D g) {
        g.setColor(new Color(205, 170, 95));
        g.fillRect(x, y, width, height);
        g.setColor(new Color(140, 100, 55));
        g.drawRect(x, y, width - 1, height - 1);
    }

    public Rectangle getBounds() {
        return new Rectangle(x, y, width, height);
    }
}

class TubePair {
    private final Tube topTube;
    private final Tube bottomTube;
    private boolean scored = false;

    public TubePair(int x, int gapY, int gapHeight, int speed) {
        int tubeWidth = Tube.TUBE_WIDTH;

        int minRockHeight = 100;

        int playableBottom = Window.HEIGHT;

        int safeGapY = Math.max(minRockHeight, gapY);
        int safeGapBottom = Math.min(playableBottom - minRockHeight, gapY + gapHeight);

        int topHeight = Math.max(minRockHeight, safeGapY);
        int bottomY = safeGapBottom;
        int bottomHeight = Math.max(minRockHeight, playableBottom - bottomY);

        topTube = new Tube(x, 0, tubeWidth, topHeight, speed, true);
        bottomTube = new Tube(x, bottomY, tubeWidth, bottomHeight, speed, false);
    }

    public void tick(int speed) {
        topTube.tick(speed);
        bottomTube.tick(speed);
    }

    public void render(Graphics2D g, ImageObserver obs) {
        topTube.render(g, obs);
        bottomTube.render(g, obs);
    }

    public boolean isOffScreen() {
        return topTube.getX() + topTube.getWidth() < 0;
    }

    public boolean collides(Bird bird) {
        Rectangle birdRect = bird.getBounds();
        return birdRect.intersects(topTube.getBounds()) ||
               birdRect.intersects(bottomTube.getBounds());
    }

    public boolean checkAndScore(Bird bird) {
        if (!scored && bird.getX() > topTube.getX() + topTube.getWidth()) {
            scored = true;
            return true;
        }
        return false;
    }

    public Tube getTopTube() {
        return topTube;
    }

    public Tube getBottomTube() {
        return bottomTube;
    }
}

class TubeColumn {
    private final List<TubePair> tubePairs;
    private final Random random;

    private int points;
    private int speed;
    private int spawnTimer;
    private int spawnDelay;
    private int gapHeight;

    // Min column speed
    private static final int START_SPEED = 5;

    // Max speed
    private static final int MAX_SPEED = 10;

    // Column frequencie
    private static final int START_SPAWN_DELAY = 95;
    private static final int MIN_SPAWN_DELAY = 72;

    // Gap
    private static final int START_GAP_HEIGHT = 170;
    private static final int MIN_GAP_HEIGHT = 115;

    // Column height
    private static final int MIN_ROCK_HEIGHT = 100;

    private static final int SPAWN_X_OFFSET = 60;

    public TubeColumn() {
        tubePairs = new ArrayList<>();
        random = new Random();

        points = 0;
        speed = START_SPEED;
        spawnTimer = 0;
        spawnDelay = START_SPAWN_DELAY;
        gapHeight = START_GAP_HEIGHT;

        addTubePair();
    }

    private void addTubePair() {
        int playableHeight = Window.HEIGHT;

        int minGapY = MIN_ROCK_HEIGHT;
        int maxGapY = playableHeight - gapHeight - MIN_ROCK_HEIGHT;

        if (maxGapY < minGapY) {
            maxGapY = minGapY;
        }

        int gapY = minGapY + random.nextInt(maxGapY - minGapY + 1);

        tubePairs.add(new TubePair(
                Window.WIDTH + SPAWN_X_OFFSET,
                gapY,
                gapHeight,
                speed
        ));
    }

    public boolean tick(Bird bird) {
        boolean scoredNow = false;

        spawnTimer++;

        Iterator<TubePair> iterator = tubePairs.iterator();
        while (iterator.hasNext()) {
            TubePair pair = iterator.next();
            pair.tick(speed);

            if (pair.checkAndScore(bird)) {
                points++;
                scoredNow = true;
                increaseDifficulty();
            }

            if (pair.isOffScreen()) {
                iterator.remove();
            }
        }

        if (spawnTimer >= spawnDelay) {
            spawnTimer = 0;
            addTubePair();
        }

        return scoredNow;
    }

    private void increaseDifficulty() {

        if (points % 5 == 0 && speed < MAX_SPEED) {
            speed++;
        }

        if (points % 4 == 0 && gapHeight > MIN_GAP_HEIGHT) {
            gapHeight -= 5;
        }

        if (points % 6 == 0 && spawnDelay > MIN_SPAWN_DELAY) {
            spawnDelay -= 2;
        }
    }

    public void render(Graphics2D g, ImageObserver obs) {
        for (TubePair pair : tubePairs) {
            pair.render(g, obs);
        }
    }

    public boolean checkCollision(Bird bird) {
        for (TubePair pair : tubePairs) {
            if (pair.collides(bird)) {
                return true;
            }
        }
        return false;
    }

    public int getPoints() {
        return points;
    }

    public int getSpeed() {
        return speed;
    }

    public List<TubePair> getTubePairs() {
        return tubePairs;
    }
}

interface IStrategy {
    void controller(Bird bird, KeyEvent kevent);
    void controllerReleased(Bird bird, KeyEvent kevent);
}

class Controller implements IStrategy {
    private final SoundManager soundManager;

    public Controller(SoundManager soundManager) {
        this.soundManager = soundManager;
    }

    @Override
    public void controller(Bird bird, KeyEvent kevent) {
        if (kevent.getKeyCode() == KeyEvent.VK_SPACE && bird != null) {
            bird.jump();
            if (soundManager != null) {
                soundManager.playJump();
            }
        }
    }

    @Override
    public void controllerReleased(Bird bird, KeyEvent kevent) {
    }
}

interface IImage {
    ImageIcon loadImage();
}

class ProxyImage implements IImage {
    private final String src;
    private RealImage realImage;

    public ProxyImage(String src) {
        this.src = src;
    }

    @Override
    public ImageIcon loadImage() {
        if (realImage == null) {
            realImage = new RealImage(src);
        }
        return realImage.loadImage();
    }
}

class RealImage implements IImage {
    private final String src;
    private ImageIcon imageIcon;

    public RealImage(String src) {
        this.src = src;
    }

    @Override
    public ImageIcon loadImage() {
        if (imageIcon == null) {
            URL resource = getClass().getResource(src);
            if (resource != null) {
                imageIcon = new ImageIcon(resource);
            } else {
                imageIcon = new ImageIcon();
            }
        }
        return imageIcon;
    }
}

class Game extends JPanel implements ActionListener {
    private boolean isRunning = false;
    private boolean gameOver = false;
    private boolean deathSequenceStarted = false;

    private Image background;
    private Image groundTileImage;
    private Image rockBottomCapOverlay;

    private Image frameLarge;
    private Image frameSmall;
    
    private Bird menuBird;
    
    private Rectangle soundButtonBounds = new Rectangle(20, 15, 150, 50);

    private Bird bird;
    private TubeColumn tubeColumn;
    private int highScore;

    private int groundOffset = 0;

    private int groundSpeed = 4;

    private SoundManager soundManager;

    private boolean hitSoundPlayed = false;
    private boolean deadSoundPlayed = false;
    private boolean gameOverSoundPlayed = false;

    private static final String HIGH_SCORE_FILE = "highscore.dat";

    public Game() {
        background = loadImage("background.jpg");
        groundTileImage = loadImage("ground_tile.png");
        rockBottomCapOverlay = loadImage("rock_bottom_cap.png");

        frameLarge = loadImage("frame_1.png");
        frameSmall = loadImage("frame_2.png");
        
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (!isRunning && soundButtonBounds.contains(e.getPoint())) {
                    if (!gameOver) {
                        soundManager.toggleMute();
                    } else {
                        soundManager.toggleMuteWithoutResume();
                    }
                    repaint();
                }
            }
        });

        soundManager = new SoundManager();

        highScore = SettingsManager.loadHighScore();
        
        menuBird = new Bird(170, 260);

        setFocusable(true);
        setDoubleBuffered(true);
        addKeyListener(new GameKeyAdapter());

        Timer timer = new Timer(15, this);
        timer.start();
    }
    
    private void drawSoundButton(Graphics2D g2) {
        int x = soundButtonBounds.x;
        int y = soundButtonBounds.y;
        int w = soundButtonBounds.width;
        int h = soundButtonBounds.height;

        if (frameSmall != null) {
            g2.drawImage(frameSmall, x, y, w, h, null);
        } else {
            g2.setColor(new Color(0, 0, 0, 120));
            g2.fillRoundRect(x, y, w, h, 20, 20);
        }

        g2.setFont(new Font("SansSerif", Font.BOLD, 20));
        g2.setColor(new Color(40, 20, 10));

        String text = soundManager.isMuted() ? "Sound: OFF" : "Sound: ON";
        FontMetrics fm = g2.getFontMetrics();
        int textX = x + (w - fm.stringWidth(text)) / 2;
        int textY = y + ((h - fm.getHeight()) / 2) + fm.getAscent();

        g2.drawString(text, textX, textY);
    }

    private Image loadImage(String fileName) {
        URL resource = getClass().getResource(fileName);
        if (resource == null) {
            return null;
        }
        ImageIcon icon = new ImageIcon(resource);
        if (icon.getIconWidth() > 0 && icon.getIconHeight() > 0) {
            return icon.getImage();
        }
        return null;
    }

    private int loadHighScore() {
        File file = new File(HIGH_SCORE_FILE);

        if (!file.exists()) {
            return 0;
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line = reader.readLine();
            if (line != null) {
                return Integer.parseInt(line.trim());
            }
        } catch (IOException | NumberFormatException e) {
            e.printStackTrace();
        }

        return 0;
    }

    private void saveHighScore() {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(HIGH_SCORE_FILE))) {
            writer.write(String.valueOf(highScore));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        Toolkit.getDefaultToolkit().sync();
        
        if (!gameOver) {
            soundManager.playMainLoop();
        }

        int groundTileWidth = 96;
        if (groundTileImage != null && groundTileImage.getWidth(null) > 0) {
            groundTileWidth = groundTileImage.getWidth(null);
        }

        if (isRunning) {
            if (tubeColumn != null) {
                groundSpeed = tubeColumn.getSpeed();
            }

            soundManager.playMainLoop();

            groundOffset -= groundSpeed;
            if (groundOffset <= -groundTileWidth) {
                groundOffset += groundTileWidth;
            }

            bird.tick();

            boolean scoredNow = tubeColumn.tick(bird);
            if (scoredNow) {
                soundManager.playScore();
            }

            if (!deathSequenceStarted && bird.getY() < 0) {
                bird.setY(0);
                bird.setVelocityY(0);
            }

            if (!deathSequenceStarted && tubeColumn.checkCollision(bird)) {
                deathSequenceStarted = true;
                bird.hitRock();
            }

            if (deathSequenceStarted) {
                if (!hitSoundPlayed) {
                    soundManager.playHit();
                    hitSoundPlayed = true;
                } else if (!deadSoundPlayed) {
                    soundManager.playDead();
                    deadSoundPlayed = true;
                }
            }

            if (bird.getY() + bird.getHeight() >= Window.HEIGHT - Window.GROUND_HEIGHT) {
                bird.setY(Window.HEIGHT - Window.GROUND_HEIGHT - bird.getHeight());

                if (!gameOverSoundPlayed) {
                    soundManager.stopMain();
                    soundManager.playGameOver();
                    gameOverSoundPlayed = true;
                }

                endGame();
            }
        }
        
        if (!isRunning && !gameOver && menuBird != null) {
            menuBird.tickIdleOnly();
            menuBird.setY(260 + (int)(Math.sin(System.currentTimeMillis() / 180.0) * 8));
        }

        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;

        drawBackground(g2);
        drawGround(g2);
        
        if (!isRunning && !gameOver && menuBird != null) {
            menuBird.render(g2, this);
        }

        if (tubeColumn != null) {
            tubeColumn.render(g2, this);
        }

        if (tubeColumn != null) {
            drawBottomCapsOverGround(g2);
        }

        if (bird != null) {
            bird.render(g2, this);
        }
        
        if (!isRunning) {
            drawSoundButton(g2);
        }

        drawHUD(g2);

        if (!isRunning) {
            drawOverlay(g2);
        }
    }

    private void drawBackground(Graphics2D g2) {
        if (background != null && background.getWidth(null) > 0) {
            g2.drawImage(background, 0, 0, getWidth(), getHeight(), null);
        } else {
            GradientPaint sky = new GradientPaint(
                    0, 0, new Color(120, 200, 255),
                    0, getHeight(), new Color(220, 245, 255)
            );
            g2.setPaint(sky);
            g2.fillRect(0, 0, getWidth(), getHeight());

            g2.setColor(new Color(255, 255, 255, 180));
            g2.fillOval(100, 80, 120, 45);
            g2.fillOval(500, 120, 140, 50);
            g2.fillOval(700, 70, 110, 40);
        }
    }

    private void drawGround(Graphics2D g2) {
        int y = Window.HEIGHT - Window.GROUND_HEIGHT;

        if (groundTileImage != null && groundTileImage.getWidth(null) > 0) {
            int tileWidth = groundTileImage.getWidth(null);

            if (tileWidth <= 0) {
                tileWidth = 96;
            }

            for (int x = groundOffset - tileWidth; x < Window.WIDTH + tileWidth; x += tileWidth) {
                g2.drawImage(groundTileImage, x, y, tileWidth, Window.GROUND_HEIGHT, null);
            }
        } else {
            g2.setColor(new Color(222, 184, 135));
            g2.fillRect(0, y, Window.WIDTH, Window.GROUND_HEIGHT);

            g2.setColor(new Color(160, 120, 70));
            g2.drawLine(0, y, Window.WIDTH, y);
        }
    }

    private void drawBottomCapsOverGround(Graphics2D g2) {
        if (tubeColumn == null) {
            return;
        }

        List<TubePair> pairs = tubeColumn.getTubePairs();
        if (pairs == null) {
            return;
        }

        for (TubePair pair : pairs) {
            Tube bottom = pair.getBottomTube();
            if (bottom == null) {
                continue;
            }

            int capHeight = 40;
            int drawY = bottom.getY() + bottom.getHeight() - capHeight;

            if (rockBottomCapOverlay != null) {
                g2.drawImage(rockBottomCapOverlay, bottom.getX(), drawY, bottom.getWidth(), capHeight, null);
            }
        }
    }

    private void drawCenteredText(Graphics2D g2, String text, int centerX, int y) {
        FontMetrics fm = g2.getFontMetrics();
        int textWidth = fm.stringWidth(text);
        g2.drawString(text, centerX - textWidth / 2, y);
    }

    private void drawSmallFrame(Graphics2D g2, int x, int y, int w, int h) {
        if (frameSmall != null) {
            g2.drawImage(frameSmall, x, y, w, h, null);
        } else {
            g2.setColor(new Color(0, 0, 0, 120));
            g2.fillRoundRect(x, y, w, h, 25, 25);
        }
    }

    private void drawLargeFrame(Graphics2D g2) {

        if (frameLarge == null) {
            return;
        }

        int imgWidth = frameLarge.getWidth(null);
        int imgHeight = frameLarge.getHeight(null);

        int x = (Window.WIDTH - imgWidth) / 2;

        int y = 0;
        
        g2.drawImage(frameLarge, x, y, null);
    }

    private void drawHUD(Graphics2D g2) {
        g2.setFont(new Font("SansSerif", Font.BOLD, 26));
        FontMetrics fm = g2.getFontMetrics();

        int paddingX = 18;

        String highScoreText = "High Score: " + highScore;
        int highTextW = fm.stringWidth(highScoreText);
        int highPanelW = highTextW + paddingX * 2;
        int highPanelH = 60;
        int highPanelX = Window.WIDTH - highPanelW - 20;
        int highPanelY = 15;

        drawSmallFrame(g2, highPanelX, highPanelY, highPanelW, highPanelH);
        g2.setColor(new Color(40, 20, 10));
        g2.drawString(highScoreText, highPanelX + paddingX, highPanelY + 38);

        if (isRunning && tubeColumn != null) {
            String scoreText = "Score: " + tubeColumn.getPoints();
            int scoreTextW = fm.stringWidth(scoreText);
            int scorePanelW = scoreTextW + paddingX * 2;
            int scorePanelH = 60;
            int scorePanelX = 20;
            int scorePanelY = 15;

            drawSmallFrame(g2, scorePanelX, scorePanelY, scorePanelW, scorePanelH);
            g2.setColor(new Color(40, 20, 10));
            g2.drawString(scoreText, scorePanelX + paddingX, scorePanelY + 38);
        }
    }

    private void drawOverlay(Graphics2D g2) {

        drawLargeFrame(g2);

        int centerX = Window.WIDTH / 2;
        int frameX = (Window.WIDTH - frameLarge.getWidth(null)) / 2;
        int frameY = 0;

        g2.setColor(new Color(60, 30, 15));

        if (gameOver) {

            // Game Over
            g2.setFont(new Font("Serif", Font.BOLD, 56));
            drawCenteredText(g2, "Game Over", centerX, 265);

            // Score
            g2.setFont(new Font("SansSerif", Font.BOLD, 30));
            drawCenteredText(g2, "Score: " + (tubeColumn != null ? tubeColumn.getPoints() : 0), centerX, 318);

            // Restart
            g2.setFont(new Font("SansSerif", Font.BOLD, 24));
            drawCenteredText(g2, "Press Enter to restart", centerX, 378);

            // Jump
            drawCenteredText(g2, "Press Space to jump", centerX, 425);

        } else {

            // Game name
            g2.setFont(new Font("Serif", Font.BOLD, 60));
            drawCenteredText(g2, "Desert Bird", centerX, 266);

            // Start
            g2.setFont(new Font("SansSerif", Font.BOLD, 28));
            drawCenteredText(g2, "Press Enter to start", centerX, 318);

            // Jump
            drawCenteredText(g2, "Press Space to jump", centerX, 378);

            // author
            g2.setFont(new Font("SansSerif", Font.BOLD, 16));
            g2.setColor(new Color(70, 40, 20));
            g2.drawString("© Jevgenij Anisimov", 25, 558);
        }
    }

    private void restartGame() {
        isRunning = true;
        gameOver = false;
        deathSequenceStarted = false;

        hitSoundPlayed = false;
        deadSoundPlayed = false;
        gameOverSoundPlayed = false;

        bird = new Bird(Window.WIDTH / 3, Window.HEIGHT / 2 - 50);
        tubeColumn = new TubeColumn();

        soundManager.stopGameOver();
        soundManager.ensureMainPlayingIfAllowed();
    }

    private void endGame() {
        isRunning = false;
        gameOver = true;
        deathSequenceStarted = false;

        if (tubeColumn != null && tubeColumn.getPoints() > highScore) {
            highScore = tubeColumn.getPoints();
            SettingsManager.saveSettings(highScore, soundManager.isMuted());
        }
    }

    class GameKeyAdapter extends KeyAdapter {
        private final Controller controller;

        public GameKeyAdapter() {
            controller = new Controller(soundManager);
        }

        @Override
        public void keyPressed(KeyEvent e) {
            if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                restartGame();
            }

            if (isRunning) {
                controller.controller(bird, e);
            }
        }

        @Override
        public void keyReleased(KeyEvent e) {
            if (isRunning) {
                controller.controllerReleased(bird, e);
            }
        }
    }
}

public class Window {

    public static final int WIDTH = 900;
    public static final int HEIGHT = 600;

    public static final int GROUND_HEIGHT = 110;

    public Window(int width, int height, String title, Game game) {
        JFrame frame = new JFrame(title);
        Image icon = Toolkit.getDefaultToolkit().getImage(getClass().getResource("icon.png"));
        frame.setIconImage(icon);
        frame.add(game);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setPreferredSize(new Dimension(width, height));
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setResizable(false);
        frame.setVisible(true);
    }

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ex) {
            ex.printStackTrace();
        }

        EventQueue.invokeLater(() -> {
            Game game = new Game();
            new Window(WIDTH, HEIGHT, "Desert Bird", game);
        });
    }
}