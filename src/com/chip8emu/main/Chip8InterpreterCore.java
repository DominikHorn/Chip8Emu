package com.chip8emu.main;

public class Chip8InterpreterCore {
	// Chip-8 specs listed @ https://en.wikipedia.org/wiki/CHIP-8
	private static final int CHIP8_PROGLOAD_ADDR = 0x200;
	private int stackPointer;
	private int addrRegister;
	private byte[] vRegisters;
	private byte[] ram;
	private byte[] vram;
	private int[] stack;
	private int delayTimer;
	private int soundTimer;

	private Thread runThread;

	private boolean isProgramLoaded;
	private boolean isRunning;

	public Chip8InterpreterCore() {
		this.vRegisters = new byte[16];
		this.addrRegister = 0;
		this.ram = new byte[4096];
		this.vram = new byte[64 * 32 / 8]; // Black and white memory -> one bit
											// per screen space
		this.stack = new int[16];
		this.delayTimer = 0;
		this.soundTimer = 0;
		this.isProgramLoaded = false;
		this.isRunning = false;
	}

	private void dumpMemory(byte[] memory) {
		System.out.print("0x00000000: ");
		short byteCount = 0;
		for (int i = 0; i < memory.length; i++) {
			System.out.print(String.format("%02X", memory[i]));

			if (++byteCount == 16) {
				byteCount = 0;
				System.out.print("\n");
				if (memory.length > i + 1)
					System.out.print(String.format("0x%08X: ", i));
			} else {
				System.out.print(" ");
			}
		}
	}

	private byte[] clearMemory(byte[] memory) {
		for (int i = 0; i < memory.length; i++)
			memory[i] = 0;

		return memory;
	}

	private int[] clearMemory(int[] memory) {
		for (int i = 0; i < memory.length; i++)
			memory[i] = 0;

		return memory;
	}

	private void clear() {
		ram = clearMemory(ram);
		vRegisters = clearMemory(vRegisters);
		stack = clearMemory(stack);
		addrRegister = 0;
		delayTimer = 0;
		soundTimer = 0;
		isProgramLoaded = false;
		isRunning = false;
	}

	private void fail(String error) {
		System.err.println(error);
		dump();
		clear();
		System.exit(-1);
	}

	private int popStack() {
		if (stackPointer > 0)
			stackPointer--;
		else
			fail("Invalid Stack access");

		return stack[stackPointer];
	}

	private void pushStack(int addr) {
		stack[stackPointer] = addr;

		if (stackPointer < stack.length - 1)
			stackPointer++;
		else
			fail("Invalid Stack access");
	}

	public boolean isProgramLoaded() {
		return isProgramLoaded;
	}

	public boolean isRunning() {
		return isRunning;
	}

	public boolean loadCode(byte[] code) {
		// Clear memory
		clear();

		// TL;DR lol
		if (code.length > ram.length - CHIP8_PROGLOAD_ADDR)
			return false;

		for (int i = 0; i < code.length; i++)
			ram[i + CHIP8_PROGLOAD_ADDR] = code[i];

		return isProgramLoaded = true;
	}

	public synchronized void dump() {
		// Print system state
		System.out.println("RAM:");
		dumpMemory(ram);
		System.out.println("\n-------------------------------------------------\n");
	}

	public synchronized void tick() {
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
					byte[] opcode = new byte[] { ram[++ip], ram[++ip] };
					byte controlHigh = (byte) ((opcode[0] & 0xF0) >> 4);
					byte controlLow = (byte) (opcode[0] & 0x0F);
					byte paramHigh = (byte) ((opcode[1] & 0xF0) >> 4);
					byte paramLow = (byte) (opcode[1] & 0x0F);

					// Stage 2 + 3: DECODE & EXECUTE
					switch (controlHigh) {
					case 0x0:
						switch (controlLow) {
						case 0x0:
							switch (paramHigh) {
							case 0xE:
								switch (paramLow) {
								case 0x0:
									clearMemory(vram);
									break;
								case 0xE:
									fail("untested");
									ip = popStack();
									break;
								default:
									fail("Invalid instruction");
									break;
								}
								break;
							default:
								fail("Invalid instruction");
								break;
							}
							break;
						default:
							fail("RCA 1802 Programs not supported ATM");
							break;

						}

						break;
					case 0x1:
						break;
					case 0x2:
						break;
					case 0x3:
						break;
					case 0x4:
						break;
					case 0x5:
						break;
					case 0x6:
						break;
					case 0x7:
						break;
					case 0x8:
						break;
					case 0x9:
						break;
					case 0xA:
						break;
					case 0xB:
						break;
					case 0xC:
						break;
					case 0xD:
						break;
					case 0xE:
						break;
					case 0xF:
						break;
					default:
						break;
					}

					// Should we exit?
					exit = Thread.interrupted() | exit;
				}
			}
		});
		runThread.start();
		isRunning = true;
	}

	public void halt() throws InterruptedException {
		if (runThread != null && runThread.isAlive()) {
			runThread.interrupt();
			runThread.join();
			runThread = null;
		}

		isRunning = false;
	}

}
