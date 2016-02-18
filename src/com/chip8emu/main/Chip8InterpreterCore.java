package com.chip8emu.main;

public class Chip8InterpreterCore {
	// Chip-8 specs listed @ https://en.wikipedia.org/wiki/CHIP-8
	private static final int CHIP8_PROGLOAD_ADDR = 0x200;
	private byte[] vRegisters;
	private char addrRegister;
	private byte[] memory;
	private byte[] stack;
	private int delayTimer;
	private int soundTimer;

	private Thread runThread;

	public Chip8InterpreterCore() {
		this.vRegisters = new byte[16];
		this.addrRegister = 0;
		this.memory = new byte[4096];
		this.stack = new byte[64];
		this.delayTimer = 0;
		this.soundTimer = 0;
	}

	private byte[] clearMemory(byte[] memory) {
		for (int i = 0; i < memory.length; i++)
			memory[i] = 0;

		return memory;
	}

	private void clear() {
		memory = clearMemory(memory);
		vRegisters = clearMemory(vRegisters);
		stack = clearMemory(stack);
		addrRegister = 0;
		delayTimer = 0;
		soundTimer = 0;
	}

	public boolean loadCode(byte[] code) {
		// Clear memory
		clear();

		// TL;DR lol
		if (code.length > memory.length - CHIP8_PROGLOAD_ADDR)
			return false;

		for (int i = 0; i < code.length; i++)
			memory[i + CHIP8_PROGLOAD_ADDR] = code[i];

		return true;
	}

	public void tick() {
		if (delayTimer > 0)
			delayTimer--;
		if (soundTimer > 0)
			soundTimer--;
	}

	public void run() {
		this.runThread = new Thread(new Runnable() {

			@Override
			public void run() {
				boolean exit = false;
				int ip = CHIP8_PROGLOAD_ADDR - 1;

				while (!exit) {
					// Stage 1: LOAD
					byte[] opcode = new byte[] { memory[++ip], memory[++ip], memory[++ip], memory[++ip] };

					// Stage 2 + 3: DECODE & EXECUTE
					switch(opcode[0]) {
						
					}

					// Should we exit?
					exit = Thread.interrupted() | exit;
				}
			}
		});
		runThread.start();
	}

	public void halt() throws InterruptedException {
		this.runThread.interrupt();
		this.runThread.join();
	}

}
