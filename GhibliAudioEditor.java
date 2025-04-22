import java.awt.*;
import java.awt.event.*;
import java.io.*;
import javax.sound.sampled.*;
import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.border.Border;

public class GhibliAudioEditor extends JFrame {
    // Core UI components
    private JButton loadButton;
    private JButton playButton;
    private JButton saveButton;
    private JSlider speedSlider;   // Controls playback speed (affects pitch/frequency)
    private JSlider volumeSlider;  // Controls amplitude (volume)
    private JLabel speedLabel;
    private JLabel volumeLabel;
    private JProgressBar progressBar;
    private WaveformPanel waveformPanel;
    
    // Effects UI components (in separate tab)
    private JCheckBox echoCheckBox;
    private JSlider echoDelaySlider;   // in ms
    private JSlider echoDecaySlider;   // percentage (0-100)
    private JCheckBox distortionCheckBox;
    private JSlider distortionSlider;  // percentage (0-100)
    private JCheckBox lowPassCheckBox;
    private JSlider lowPassCutoffSlider; // cutoff frequency in Hz

    // Audio data and settings
    private File audioFile;
    private byte[] audioBytes;
    private AudioFormat originalFormat;
    private volatile boolean playing = false;
    private volatile int currentFrame = 0;
    private int previewFrameCount = 10000;

    public GhibliAudioEditor() {
        super("Ghibli Audio Editor");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());
        setBackground(Color.DARK_GRAY);

        // --- Basic Controls Panel ---
        JPanel basicPanel = new JPanel(new BorderLayout());
        basicPanel.setBackground(Color.DARK_GRAY);
        
        // Top row: load, play/pause, and save buttons.
        JPanel topPanel = new JPanel(new FlowLayout());
        topPanel.setBackground(Color.DARK_GRAY);
        loadButton = new JButton("Load Audio File");
        playButton = new JButton("Play/Pause");
        saveButton = new JButton("Save Edited Audio");
        customizeButton(loadButton);
        customizeButton(playButton);
        customizeButton(saveButton);
        topPanel.add(loadButton);
        topPanel.add(playButton);
        topPanel.add(saveButton);
        basicPanel.add(topPanel, BorderLayout.NORTH);

        // Center: speed/volume sliders and waveform preview.
        JPanel centerPanel = new JPanel(new BorderLayout());
        centerPanel.setBackground(Color.DARK_GRAY);
        JPanel sliderPanel = new JPanel(new GridLayout(2, 3, 5, 5));
        sliderPanel.setBackground(Color.DARK_GRAY);
        speedSlider = new JSlider(JSlider.HORIZONTAL, 50, 200, 100);
        volumeSlider = new JSlider(JSlider.HORIZONTAL, 0, 200, 100);
        customizeSlider(speedSlider);
        customizeSlider(volumeSlider);
        speedLabel = new JLabel("Speed: 1.00x");
        volumeLabel = new JLabel("Volume: 100%");
        customizeLabel(speedLabel);
        customizeLabel(volumeLabel);
        sliderPanel.add(new JLabel("Speed (Pitch/Frequency):"));
        sliderPanel.add(speedSlider);
        sliderPanel.add(speedLabel);
        sliderPanel.add(new JLabel("Volume (Amplitude):"));
        sliderPanel.add(volumeSlider);
        sliderPanel.add(volumeLabel);
        centerPanel.add(sliderPanel, BorderLayout.NORTH);
        
        waveformPanel = new WaveformPanel();
        centerPanel.add(waveformPanel, BorderLayout.CENTER);
        basicPanel.add(centerPanel, BorderLayout.CENTER);
        
        // Bottom: progress bar.
        progressBar = new JProgressBar(0, 100);
        progressBar.setBackground(Color.GRAY);
        progressBar.setForeground(new Color(255, 160, 122)); // light salmon accent
        basicPanel.add(progressBar, BorderLayout.SOUTH);

        // --- Effects Panel ---
        JPanel effectsPanel = new JPanel(new GridLayout(0, 2, 5, 5));
        effectsPanel.setBackground(Color.DARK_GRAY);
        effectsPanel.setBorder(BorderFactory.createTitledBorder(BorderFactory.createLineBorder(new Color(255, 160, 122)), "Effects Options"));
        customizeTitledBorder(effectsPanel.getBorder());
        
        echoCheckBox = new JCheckBox("Echo");
        echoDelaySlider = new JSlider(JSlider.HORIZONTAL, 50, 500, 200);
        echoDecaySlider = new JSlider(JSlider.HORIZONTAL, 0, 100, 50);
        distortionCheckBox = new JCheckBox("Distortion");
        distortionSlider = new JSlider(JSlider.HORIZONTAL, 0, 100, 0);
        lowPassCheckBox = new JCheckBox("Low-Pass Filter");
        lowPassCutoffSlider = new JSlider(JSlider.HORIZONTAL, 500, 5000, 2000);
        customizeCheckBox(echoCheckBox);
        customizeCheckBox(distortionCheckBox);
        customizeCheckBox(lowPassCheckBox);
        customizeSlider(echoDelaySlider);
        customizeSlider(echoDecaySlider);
        customizeSlider(distortionSlider);
        customizeSlider(lowPassCutoffSlider);
        
        effectsPanel.add(echoCheckBox);
        effectsPanel.add(new JLabel("Echo Delay (ms):"));
        effectsPanel.add(echoDelaySlider);
        effectsPanel.add(new JLabel("Echo Decay (%):"));
        effectsPanel.add(echoDecaySlider);
        effectsPanel.add(distortionCheckBox);
        effectsPanel.add(new JLabel("Distortion Level (%):"));
        effectsPanel.add(distortionSlider);
        effectsPanel.add(lowPassCheckBox);
        effectsPanel.add(new JLabel("Low-Pass Cutoff (Hz):"));
        effectsPanel.add(lowPassCutoffSlider);

        // --- Tabbed Pane ---
        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.addTab("Basic Controls", basicPanel);
        tabbedPane.addTab("Effects", effectsPanel);
        add(tabbedPane, BorderLayout.CENTER);
        
        // --- Listeners ---
        // Basic slider listeners update labels and preview waveform.
        ChangeListener sliderListener = new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                double speedFactor = speedSlider.getValue() / 100.0;
                speedLabel.setText(String.format("Speed: %.2fx", speedFactor));
                volumeLabel.setText("Volume: " + volumeSlider.getValue() + "%");
                updatePreviewWaveform();
            }
        };
        speedSlider.addChangeListener(sliderListener);
        volumeSlider.addChangeListener(sliderListener);
        
        // Effects listeners update preview.
        ChangeListener effectsListener = new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                updatePreviewWaveform();
            }
        };
        echoDelaySlider.addChangeListener(effectsListener);
        echoDecaySlider.addChangeListener(effectsListener);
        distortionSlider.addChangeListener(effectsListener);
        lowPassCutoffSlider.addChangeListener(effectsListener);
        echoCheckBox.addActionListener(e -> updatePreviewWaveform());
        distortionCheckBox.addActionListener(e -> updatePreviewWaveform());
        lowPassCheckBox.addActionListener(e -> updatePreviewWaveform());

        // Button actions
        loadButton.addActionListener(e -> loadAudio());
        playButton.addActionListener(e -> togglePlayback());
        saveButton.addActionListener(e -> saveEditedAudio());
        
        setSize(800, 550);
        setLocationRelativeTo(null);
        setVisible(true);
    }

    // Helper methods to customize UI components for dark mode & a Ghibli-inspired theme
    private void customizeButton(JButton btn) {
        btn.setBackground(new Color(60, 63, 65));
        btn.setForeground(Color.WHITE);
        btn.setFocusPainted(false);
    }
    
    private void customizeSlider(JSlider slider) {
        slider.setBackground(Color.DARK_GRAY);
        slider.setForeground(new Color(255, 160, 122)); // warm accent color
    }
    
    private void customizeLabel(JLabel label) {
        label.setForeground(Color.WHITE);
        label.setFont(new Font("Serif", Font.PLAIN, 14));
    }
    
    private void customizeCheckBox(JCheckBox cb) {
        cb.setBackground(Color.DARK_GRAY);
        cb.setForeground(Color.WHITE);
    }
    
    private void customizeTitledBorder(Border border) {
        // In a real app you might cast and set title color; for simplicity, we'll leave it as default.
    }
    
    private void loadAudio() {
        JFileChooser chooser = new JFileChooser();
        int result = chooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            audioFile = chooser.getSelectedFile();
            try {
                AudioInputStream inStream = AudioSystem.getAudioInputStream(audioFile);
                AudioFormat baseFormat = inStream.getFormat();
                AudioFormat decodedFormat = new AudioFormat(
                        AudioFormat.Encoding.PCM_SIGNED,
                        baseFormat.getSampleRate(),
                        16,
                        baseFormat.getChannels(),
                        baseFormat.getChannels() * 2,
                        baseFormat.getSampleRate(),
                        false);
                AudioInputStream din = AudioSystem.getAudioInputStream(decodedFormat, inStream);
                originalFormat = din.getFormat();

                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = din.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                }
                audioBytes = out.toByteArray();
                din.close();

                waveformPanel.setAudioData(audioBytes, originalFormat);
                updatePreviewWaveform();
                JOptionPane.showMessageDialog(this, "Audio loaded successfully.");
            } catch (Exception ex) {
                ex.printStackTrace();
                JOptionPane.showMessageDialog(this, "Error loading audio: " + ex.getMessage());
            }
        }
    }

    private void togglePlayback() {
        if (audioBytes == null) {
            JOptionPane.showMessageDialog(this, "Please load an audio file first.");
            return;
        }
        if (!playing) {
            playing = true;
            playButton.setText("Pause");
            new Thread(this::dynamicPlay).start();
        } else {
            playing = false;
            playButton.setText("Play");
        }
    }

    private void dynamicPlay() {
        int channels = originalFormat.getChannels();
        int sampleSizeBytes = originalFormat.getSampleSizeInBits() / 8;
        int frameSize = originalFormat.getFrameSize();
        int totalFrames = audioBytes.length / frameSize;
        currentFrame = 0;

        boolean echoEnabled = echoCheckBox.isSelected();
        int echoDelaySamples = (int) (echoDelaySlider.getValue() * originalFormat.getSampleRate() / 1000.0);
        double echoDecay = echoDecaySlider.getValue() / 100.0;
        int[][] echoBuffer = null;
        int[] echoBufferIndex = null;
        if (echoEnabled) {
            echoBuffer = new int[channels][echoDelaySamples];
            echoBufferIndex = new int[channels];
        }
        boolean distortionEnabled = distortionCheckBox.isSelected();
        double distortionThreshold = 32767 * (1.0 - distortionSlider.getValue() / 100.0);
        boolean lowPassEnabled = lowPassCheckBox.isSelected();
        double[] prevSample = new double[channels];
        double lowPassCutoff = lowPassCutoffSlider.getValue();
        double dt = 1.0 / originalFormat.getSampleRate();
        double RC = 1.0 / (2 * Math.PI * lowPassCutoff);
        double alpha = dt / (RC + dt);

        try {
            DataLine.Info info = new DataLine.Info(SourceDataLine.class, originalFormat);
            SourceDataLine line = (SourceDataLine) AudioSystem.getLine(info);
            line.open(originalFormat);
            line.start();

            byte[] outputBuffer = new byte[4096];
            int outputBufferPos = 0;
            double position = 0.0;

            while (playing && position < totalFrames) {
                double speedFactor = speedSlider.getValue() / 100.0;
                double volumeFactor = volumeSlider.getValue() / 100.0;
                if (outputBufferPos + frameSize > outputBuffer.length) {
                    line.write(outputBuffer, 0, outputBufferPos);
                    outputBufferPos = 0;
                }
                int frameIndex1 = (int) Math.floor(position);
                int frameIndex2 = (frameIndex1 + 1 < totalFrames) ? frameIndex1 + 1 : frameIndex1;
                double weight = position - frameIndex1;
                for (int ch = 0; ch < channels; ch++) {
                    int offset1 = frameIndex1 * frameSize + ch * sampleSizeBytes;
                    int offset2 = frameIndex2 * frameSize + ch * sampleSizeBytes;
                    int sample1 = ((audioBytes[offset1 + 1] << 8) | (audioBytes[offset1] & 0xff));
                    int sample2 = ((audioBytes[offset2 + 1] << 8) | (audioBytes[offset2] & 0xff));
                    double interpolated = sample1 * (1.0 - weight) + sample2 * weight;
                    int sample = (int) (interpolated * volumeFactor);
                    sample = applyEffects(sample, ch, echoEnabled, echoDecay, echoBuffer, echoBufferIndex,
                                          distortionEnabled, distortionThreshold,
                                          lowPassEnabled, prevSample, alpha);
                    outputBuffer[outputBufferPos++] = (byte) (sample & 0xff);
                    outputBuffer[outputBufferPos++] = (byte) ((sample >> 8) & 0xff);
                }
                position += speedFactor;
                currentFrame = (int) position;
                final int progress = (int) ((100.0 * currentFrame) / totalFrames);
                SwingUtilities.invokeLater(() -> progressBar.setValue(progress));
            }
            if (outputBufferPos > 0) {
                line.write(outputBuffer, 0, outputBufferPos);
            }
            line.drain();
            line.stop();
            line.close();
        } catch (LineUnavailableException e) {
            e.printStackTrace();
        }
        SwingUtilities.invokeLater(() -> playButton.setText("Play"));
        playing = false;
    }

    private int applyEffects(int sample, int ch, boolean echoEnabled, double echoDecay,
                             int[][] echoBuffer, int[] echoBufferIndex,
                             boolean distortionEnabled, double distortionThreshold,
                             boolean lowPassEnabled, double[] prevSample, double alpha) {
        if (echoEnabled && echoBuffer != null && echoBufferIndex != null) {
            int delayed = echoBuffer[ch][echoBufferIndex[ch]];
            sample = sample + (int) (echoDecay * delayed);
            echoBuffer[ch][echoBufferIndex[ch]] = sample;
            echoBufferIndex[ch] = (echoBufferIndex[ch] + 1) % echoBuffer[ch].length;
        }
        if (distortionEnabled) {
            if (sample > distortionThreshold) sample = (int) distortionThreshold;
            if (sample < -distortionThreshold) sample = (int) -distortionThreshold;
        }
        if (lowPassEnabled) {
            sample = (int) (alpha * sample + (1 - alpha) * prevSample[ch]);
            prevSample[ch] = sample;
        }
        if (sample > 32767) sample = 32767;
        if (sample < -32768) sample = -32768;
        return sample;
    }

    private byte[] processPreviewAudio(double speedFactor, double volumeFactor) {
        int channels = originalFormat.getChannels();
        int sampleSizeBytes = originalFormat.getSampleSizeInBits() / 8;
        int frameSize = originalFormat.getFrameSize();
        int totalFrames = audioBytes.length / frameSize;
        int framesToProcess = Math.min(previewFrameCount, totalFrames);
        int newFrameCount = (int) (framesToProcess / speedFactor);
        ByteArrayOutputStream outStream = new ByteArrayOutputStream();
        
        boolean echoEnabled = echoCheckBox.isSelected();
        int echoDelaySamples = (int) (echoDelaySlider.getValue() * originalFormat.getSampleRate() / 1000.0);
        double echoDecay = echoDecaySlider.getValue() / 100.0;
        int[][] echoBuffer = null;
        int[] echoBufferIndex = null;
        if (echoEnabled) {
            echoBuffer = new int[channels][echoDelaySamples];
            echoBufferIndex = new int[channels];
        }
        boolean distortionEnabled = distortionCheckBox.isSelected();
        double distortionThreshold = 32767 * (1.0 - distortionSlider.getValue() / 100.0);
        boolean lowPassEnabled = lowPassCheckBox.isSelected();
        double[] prevSample = new double[channels];
        double lowPassCutoff = lowPassCutoffSlider.getValue();
        double dt = 1.0 / originalFormat.getSampleRate();
        double RC = 1.0 / (2 * Math.PI * lowPassCutoff);
        double alpha = dt / (RC + dt);
        
        for (int i = 0; i < newFrameCount; i++) {
            double inFramePos = i * speedFactor;
            int frameIndex1 = (int) Math.floor(inFramePos);
            int frameIndex2 = (frameIndex1 + 1 < framesToProcess) ? frameIndex1 + 1 : frameIndex1;
            double weight = inFramePos - frameIndex1;
            for (int ch = 0; ch < channels; ch++) {
                int offset1 = frameIndex1 * frameSize + ch * sampleSizeBytes;
                int offset2 = frameIndex2 * frameSize + ch * sampleSizeBytes;
                int sample1 = ((audioBytes[offset1 + 1] << 8) | (audioBytes[offset1] & 0xff));
                int sample2 = ((audioBytes[offset2 + 1] << 8) | (audioBytes[offset2] & 0xff));
                double interpolated = sample1 * (1.0 - weight) + sample2 * weight;
                int sample = (int) (interpolated * volumeFactor);
                sample = applyEffects(sample, ch, echoEnabled, echoDecay, echoBuffer, echoBufferIndex,
                                      distortionEnabled, distortionThreshold,
                                      lowPassEnabled, prevSample, alpha);
                outStream.write(sample & 0xff);
                outStream.write((sample >> 8) & 0xff);
            }
        }
        return outStream.toByteArray();
    }

    private byte[] processEntireAudio(double speedFactor, double volumeFactor) {
        int channels = originalFormat.getChannels();
        int sampleSizeBytes = originalFormat.getSampleSizeInBits() / 8;
        int frameSize = originalFormat.getFrameSize();
        int totalFrames = audioBytes.length / frameSize;
        int newFrameCount = (int) (totalFrames / speedFactor);
        ByteArrayOutputStream outStream = new ByteArrayOutputStream();
        
        boolean echoEnabled = echoCheckBox.isSelected();
        int echoDelaySamples = (int) (echoDelaySlider.getValue() * originalFormat.getSampleRate() / 1000.0);
        double echoDecay = echoDecaySlider.getValue() / 100.0;
        int[][] echoBuffer = null;
        int[] echoBufferIndex = null;
        if (echoEnabled) {
            echoBuffer = new int[channels][echoDelaySamples];
            echoBufferIndex = new int[channels];
        }
        boolean distortionEnabled = distortionCheckBox.isSelected();
        double distortionThreshold = 32767 * (1.0 - distortionSlider.getValue() / 100.0);
        boolean lowPassEnabled = lowPassCheckBox.isSelected();
        double[] prevSample = new double[channels];
        double lowPassCutoff = lowPassCutoffSlider.getValue();
        double dt = 1.0 / originalFormat.getSampleRate();
        double RC = 1.0 / (2 * Math.PI * lowPassCutoff);
        double alpha = dt / (RC + dt);
        
        for (int i = 0; i < newFrameCount; i++) {
            double inFramePos = i * speedFactor;
            int frameIndex1 = (int) Math.floor(inFramePos);
            int frameIndex2 = (frameIndex1 + 1 < totalFrames) ? frameIndex1 + 1 : frameIndex1;
            double weight = inFramePos - frameIndex1;
            for (int ch = 0; ch < channels; ch++) {
                int offset1 = frameIndex1 * frameSize + ch * sampleSizeBytes;
                int offset2 = frameIndex2 * frameSize + ch * sampleSizeBytes;
                int sample1 = ((audioBytes[offset1 + 1] << 8) | (audioBytes[offset1] & 0xff));
                int sample2 = ((audioBytes[offset2 + 1] << 8) | (audioBytes[offset2] & 0xff));
                double interpolated = sample1 * (1.0 - weight) + sample2 * weight;
                int sample = (int) (interpolated * volumeFactor);
                sample = applyEffects(sample, ch, echoEnabled, echoDecay, echoBuffer, echoBufferIndex,
                                      distortionEnabled, distortionThreshold,
                                      lowPassEnabled, prevSample, alpha);
                outStream.write(sample & 0xff);
                outStream.write((sample >> 8) & 0xff);
            }
        }
        return outStream.toByteArray();
    }

    private void saveEditedAudio() {
        if (audioBytes == null) {
            JOptionPane.showMessageDialog(this, "Please load an audio file first.");
            return;
        }
        double speedFactor = speedSlider.getValue() / 100.0;
        double volumeFactor = volumeSlider.getValue() / 100.0;
        byte[] processedBytes = processEntireAudio(speedFactor, volumeFactor);
        AudioFormat processedFormat = new AudioFormat(
                originalFormat.getEncoding(),
                originalFormat.getSampleRate(),
                originalFormat.getSampleSizeInBits(),
                originalFormat.getChannels(),
                originalFormat.getFrameSize(),
                originalFormat.getFrameRate(),
                originalFormat.isBigEndian());
        ByteArrayInputStream bais = new ByteArrayInputStream(processedBytes);
        int newFrameCount = processedBytes.length / processedFormat.getFrameSize();
        AudioInputStream processedAudioStream = new AudioInputStream(bais, processedFormat, newFrameCount);
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Save Edited Audio As");
        int userSelection = fileChooser.showSaveDialog(this);
        if (userSelection == JFileChooser.APPROVE_OPTION) {
            File outFile = fileChooser.getSelectedFile();
            try {
                AudioSystem.write(processedAudioStream, AudioFileFormat.Type.WAVE, outFile);
                JOptionPane.showMessageDialog(this, "File saved successfully.");
            } catch (IOException ex) {
                ex.printStackTrace();
                JOptionPane.showMessageDialog(this, "Error saving audio: " + ex.getMessage());
            }
        }
    }

    private void updatePreviewWaveform() {
        if (audioBytes == null || originalFormat == null) return;
        final double speedFactor = speedSlider.getValue() / 100.0;
        final double volumeFactor = volumeSlider.getValue() / 100.0;
        new Thread(() -> {
            byte[] previewBytes = processPreviewAudio(speedFactor, volumeFactor);
            AudioFormat previewFormat = new AudioFormat(
                    originalFormat.getEncoding(),
                    originalFormat.getSampleRate(),
                    originalFormat.getSampleSizeInBits(),
                    originalFormat.getChannels(),
                    originalFormat.getFrameSize(),
                    originalFormat.getFrameRate(),
                    originalFormat.isBigEndian());
            SwingUtilities.invokeLater(() -> waveformPanel.setAudioData(previewBytes, previewFormat));
        }).start();
    }

    class WaveformPanel extends JPanel {
        private int[] amplitudes;

        public WaveformPanel() {
            setPreferredSize(new Dimension(600, 150));
            setBackground(Color.BLACK);
        }
        
        public void setAudioData(byte[] audioData, AudioFormat format) {
            int channels = format.getChannels();
            int sampleSizeBytes = format.getSampleSizeInBits() / 8;
            int frameSize = format.getFrameSize();
            int totalFrames = audioData.length / frameSize;
            amplitudes = new int[totalFrames];
            for (int i = 0; i < totalFrames; i++) {
                int sum = 0;
                for (int ch = 0; ch < channels; ch++) {
                    int offset = i * frameSize + ch * sampleSizeBytes;
                    int sample = ((audioData[offset + 1] << 8) | (audioData[offset] & 0xff));
                    sum += Math.abs(sample);
                }
                amplitudes[i] = sum / channels;
            }
            repaint();
        }
        
        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (amplitudes == null || amplitudes.length == 0) return;
            int width = getWidth();
            int height = getHeight();
            g.setColor(new Color(255, 160, 122));
            int step = Math.max(1, amplitudes.length / width);
            for (int x = 0; x < width; x++) {
                int index = x * step;
                if (index < amplitudes.length) {
                    int amp = amplitudes[index];
                    int y = (int) ((amp / 32768.0) * height);
                    g.drawLine(x, height/2 - y/2, x, height/2 + y/2);
                }
            }
        }
    }

    public static void main(String[] args) {
        // Set UIManager properties for a dark look and Ghibli-inspired accents.
        UIManager.put("Panel.background", Color.DARK_GRAY);
        UIManager.put("OptionPane.background", Color.DARK_GRAY);
        UIManager.put("OptionPane.messageForeground", Color.WHITE);
        UIManager.put("Button.background", new Color(60, 63, 65));
        UIManager.put("Button.foreground", Color.WHITE);
        UIManager.put("Label.foreground", Color.WHITE);
        UIManager.put("Slider.background", Color.DARK_GRAY);
        UIManager.put("Slider.foreground", new Color(255, 160, 122));
        SwingUtilities.invokeLater(() -> new GhibliAudioEditor());
    }
}
