package com.chip8emu.main;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import javax.swing.JFileChooser;
import javax.swing.filechooser.FileNameExtensionFilter;

import org.newdawn.slick.*;

public class EmuMain extends BasicGame {
	public static void main(String[] argv) {
		try {
			AppGameContainer appGC;
			appGC = new AppGameContainer(new EmuMain("Chip-8 Emulator"));
			appGC.setDisplayMode(CHIP8_DISPLAY_SCALE * 64, CHIP8_DISPLAY_SCALE * 32, false);
			appGC.setAlwaysRender(true);
			appGC.start();
		} catch (SlickException e) {
			e.printStackTrace();
		}
	}

	private final static short CHIP8_DISPLAY_SCALE = 16;

	private Chip8InterpreterCore interpreter;
	private Map<Integer, Integer> acceptedKeyMapping;
	private byte[] vram_buffer;

	public EmuMain(String gameName) {
		super(gameName);

		this.interpreter = new Chip8InterpreterCore();
		this.acceptedKeyMapping = new HashMap<>();

		// Setup input
		acceptedKeyMapping.put(Input.KEY_1, 0x0);
		acceptedKeyMapping.put(Input.KEY_2, 0x1);
		acceptedKeyMapping.put(Input.KEY_3, 0x2);
		acceptedKeyMapping.put(Input.KEY_4, 0x3);
		acceptedKeyMapping.put(Input.KEY_Q, 0x4);
		acceptedKeyMapping.put(Input.KEY_W, 0x5);
		acceptedKeyMapping.put(Input.KEY_E, 0x6);
		acceptedKeyMapping.put(Input.KEY_R, 0x7);
		acceptedKeyMapping.put(Input.KEY_A, 0x8);
		acceptedKeyMapping.put(Input.KEY_S, 0x9);
		acceptedKeyMapping.put(Input.KEY_D, 0xA);
		acceptedKeyMapping.put(Input.KEY_F, 0xB);
		acceptedKeyMapping.put(Input.KEY_Y, 0xC);
		acceptedKeyMapping.put(Input.KEY_X, 0xD);
		acceptedKeyMapping.put(Input.KEY_C, 0xE);
		acceptedKeyMapping.put(Input.KEY_V, 0xF);
	}

	@Override
	public void render(GameContainer gc, Graphics g) throws SlickException {
		// Retrieve new VRAM buffer only when interpreter has drawn
		if (interpreter.hasDrawn())
			vram_buffer = interpreter.getVRAM();

		if (vram_buffer != null)
			synchronized (vram_buffer) {
				// for each bit
				for (int y = 0; y < 32; y++) {
					for (int x = 0; x < 64; x++) {
						if (vram_buffer[x + y * 64] != 0) {
							g.fillRect(x * CHIP8_DISPLAY_SCALE, y * CHIP8_DISPLAY_SCALE, CHIP8_DISPLAY_SCALE,
									CHIP8_DISPLAY_SCALE);
						}

					}
				}
			}

	}

	@Override
	public void init(GameContainer gc) throws SlickException {
		programChange();

		// Setup input listening
		gc.getInput().addKeyListener(this);
	}

	@Override
	public void update(GameContainer gc, int delta) throws SlickException {
		if (interpreter.isProgramLoaded())
			interpreter.tick();
	}

	@Override
	public void keyPressed(int key, char c) {
		if (acceptedKeyMapping.containsKey(key))
			interpreter.inputPressed(acceptedKeyMapping.get(key));

		if (key == Input.KEY_F5)
			programChange();
	}

	@Override
	public void keyReleased(int key, char c) {
		if (acceptedKeyMapping.containsKey(key))
			interpreter.inputReleased(acceptedKeyMapping.get(key));
	}

	private void programChange() {
		try {
			interpreter.halt();
		} catch (InterruptedException e1) {
			e1.printStackTrace();
		}

		try {
			if (!loadProgram())
				System.exit(-1);
			interpreter.run();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private boolean loadProgram() throws IOException {
		final JFileChooser fc = new JFileChooser();
		fc.setCurrentDirectory(new File("D:\\Programming\\Eclipse\\Emulator\\Chip-8 Pack\\"));
		fc.setFileFilter(new FileNameExtensionFilter("Chip-8 ROM", "ch8"));
		if (fc.showOpenDialog(null) == JFileChooser.APPROVE_OPTION)
			return interpreter.loadCode(Files.readAllBytes(Paths.get(fc.getSelectedFile().getAbsolutePath())));
		else
			return false;
	}

}
