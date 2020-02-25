package ch.obermuhlner.intruder;

import com.github.sarxos.webcam.Webcam;
import com.github.sarxos.webcam.WebcamException;

import javax.imageio.ImageIO;
import javax.sound.sampled.*;
import javax.swing.*;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

public class Application {
    private final Properties properties;

    private Webcam webcam;

    private boolean alertTriggered = false;

    Application() {
        properties = new Properties();
        try (FileReader fileReader = new FileReader("intruder-alert.properties")) {
            properties.load(fileReader);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void start() {
        try {
            List<Webcam> webcams = Webcam.getWebcams();
            String cameraName = properties.getProperty("camera.name", "Undefined");
            for (Webcam webcam1 : webcams) {
                if (webcam1.getName().contains(cameraName)) {
                    webcam = webcam1;
                }
            }
            if (webcam == null && webcams.size() > 0) {
                webcam = webcams.get(0);
            }

            if (webcam !=  null) {
                int width = Integer.parseInt(properties.getProperty("camera.width", "640"));
                int height = Integer.parseInt(properties.getProperty("camera.height", "480"));
                webcam.setViewSize(new Dimension(width, height));

                if (webcam.open()) {
                    System.out.println("Camera = " + webcam.getName());
                } else {
                    webcam = null;
                }
            }
        } catch (WebcamException ex) {
            ex.printStackTrace();
            webcam = null;
        }

        JFrame frame = new JFrame();
        if (getBooleanProperty("maximize")) {
            frame.setExtendedState(JFrame.MAXIMIZED_BOTH);
            frame.setUndecorated(true);
        }
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        frame.setLayout(new BorderLayout());

        ImageIcon screenshotImage = new ImageIcon(properties.getProperty("screenshot.image", "screenshot.png"));

        JLabel mainImageLabel = new JLabel(screenshotImage);
        frame.add(mainImageLabel, BorderLayout.CENTER);

        if (getBooleanProperty("alert.on.focuslost")) {
            mainImageLabel.setFocusable(true);
            mainImageLabel.requestFocus();
            mainImageLabel.addFocusListener(new FocusAdapter() {
                @Override
                public void focusLost(FocusEvent e) {
                    super.focusLost(e);
                    triggerAlert(mainImageLabel);
                }
            });
        }
        if (getBooleanProperty("alert.on.mouseclick", true)) {
            mainImageLabel.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    if (e.getY() < 5) {
                        System.exit(0);
                    } else {
                        triggerAlert(mainImageLabel);
                    }
                }
            });
        }

        frame.pack();
        frame.setVisible(true);
    }

    private boolean getBooleanProperty(String key) {
        return getBooleanProperty(key, false);
    }

    private boolean getBooleanProperty(String key, boolean defaultValue) {
        return Boolean.parseBoolean(properties.getProperty(key, String.valueOf(defaultValue)));
    }

    private void triggerAlert(JLabel imageLabel) {
        if (alertTriggered) {
            return;
        }
        alertTriggered = true;

        playAlertSound();

        ImageIcon alertImage = new ImageIcon(properties.getProperty("alert.image", "alert.png"));
        imageLabel.setIcon(alertImage);

        new Thread(() -> {
            int imageCount = 0;
            while (true) {
                if (webcam != null) {
                    for (int i = 0; i < 5; i++) {
                        imageCount++;
                        BufferedImage webcamImage = webcam.getImage();
                        if (webcamImage != null) {
                            try {
                                ImageIO.write(webcamImage, "PNG", new File("camera" + String.format("%04d", imageCount) + ".png"));
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                            imageLabel.setIcon(new ImageIcon(webcamImage));
                        }
                        try {
                            Thread.sleep(100);
                        } catch (InterruptedException e) {
                            // ignore
                        }
                    }
                }
                imageLabel.setIcon(alertImage);
            }
        }).start();
    }

    private void playAlertSound() {
        try (AudioInputStream audioInputStream = AudioSystem.getAudioInputStream(new File(properties.getProperty("alert.sound", "alert.wav")))) {
            Clip clip = AudioSystem.getClip();
            clip.open(audioInputStream);
            clip.loop(Clip.LOOP_CONTINUOUSLY);
        } catch (UnsupportedAudioFileException | IOException | LineUnavailableException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        Application application = new Application();
        application.start();
    }

}
