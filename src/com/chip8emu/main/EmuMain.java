package com.chip8emu.main;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

import javax.swing.JFileChooser;
import javax.swing.filechooser.FileNameExtensionFilter;

import org.newdawn.slick.*;

public class EmuMain extends BasicGame {
	public static void main(String[] argv) {
		try {
			AppGameContainer appGC;
			appGC = new AppGameContainer(new EmuMain("Chip-8 Emulator"));
			appGC.setDisplayMode(CHIP8_DISPLAY_SCALE * 64, CHIP8_DISPLAY_SCALE * 32, false);
			appGC.start();
		} catch (SlickException e) {
			e.printStackTrace();
		}
	}

	private final static short CHIP8_DISPLAY_SCALE = 16;

	private Chip8InterpreterCore interpreter;

	public EmuMain(String gameName) {
		super(gameName);

		this.interpreter = new Chip8InterpreterCore();
	}

	@Override
	public void render(GameContainer arg0, Graphics arg1) throws SlickException {
		// TODO Auto-generated method stub

	}

	@Override
	public void init(GameContainer arg0) throws SlickException {
		programChange();
	}

	@Override
	public void update(GameContainer arg0, int arg1) throws SlickException {
		if (interpreter.isProgramLoaded())
			interpreter.tick();
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
