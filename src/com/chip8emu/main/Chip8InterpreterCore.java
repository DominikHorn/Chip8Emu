package com.chip8emu.main;

import java.util.Random;

public class Chip8InterpreterCore {
	private static final String ERROR_INVALID_INSTRUCTION = "Instruction invalid";
	private static final String ERROR_INVALID_STACK_ACCESS = "Stack access invalid";
	private static final String ERROR_INVALID_INPUT_ACCESS = "Input access invalid";
	private static final String ERROR_FONT_TO_BIG = "Font can not be loaded as it is too big in size";
	private static final String ERROR_RCA_1802_UNSUPPORTED = "RCA 1802 Programs not supported ATM";

	private static final boolean DEBUG_OUTPUT = true;

	// Chip-8 specs listed @ https://en.wikipedia.org/wiki/CHIP-8
	private static final int CHIP8_PROGLOAD_ADDR = 0x200;
	// @formatter:off
	private static final byte[] CHIP8_FONT_DATA = new byte[]
				   {(byte) 0xF0, (byte) 0x90, (byte) 0x90, (byte) 0x90, (byte)0xF0,	// 0
				    (byte) 0x10, (byte) 0x10, (byte) 0x10, (byte) 0x10, (byte)0x10,	// 1
					(byte) 0xF0, (byte) 0x10, (byte) 0xF0, (byte) 0x80, (byte)0xF0,	// 2
					(byte) 0xF0, (byte) 0x10, (byte) 0x70, (byte) 0x10, (byte)0xF0,	// 3
					(byte) 0x90, (byte) 0x90, (byte) 0xF0, (byte) 0x10, (byte)0x10,	// 4
					(byte) 0xF0, (byte) 0x80, (byte) 0xF0, (byte) 0x10, (byte)0xF0,	// 5
					(byte) 0x80, (byte) 0x80, (byte) 0xF0, (byte) 0x90, (byte)0xF0,	// 6
					(byte) 0xF0, (byte) 0x10, (byte) 0x10, (byte) 0x10, (byte)0x10,	// 7
					(byte) 0xF0, (byte) 0x90, (byte) 0xF0, (byte) 0x90, (byte)0xF0,	// 8
					(byte) 0xF0, (byte) 0x90, (byte) 0xF0, (byte) 0x10, (byte)0xF0,	// 9
					(byte) 0xF0, (byte) 0x90, (byte) 0xF0, (byte) 0x90, (byte)0x90,	// A
					(byte) 0xE0, (byte) 0x90, (byte) 0xF0, (byte) 0x90, (byte)0xE0,	// B
					(byte) 0xF0, (byte) 0x80, (byte) 0x80, (byte) 0x80, (byte)0xF0,	// C
					(byte) 0xE0, (byte) 0x90, (byte) 0x90, (byte) 0x90, (byte)0xE0, // D
					(byte) 0xF0, (byte) 0x80, (byte) 0xE0, (byte) 0x80, (byte)0xF0, // E
					(byte) 0xF0, (byte) 0x80, (byte) 0xE0, (byte) 0x80, (byte)0x80};// F
	// @formatter:on
	private int instructionPointer;
	private int stackPointer;
	private int addrRegister;
	private int[] stack;
	private byte[] input;
	private byte[] vRegisters;
	private byte[] ram;
	private byte[] vram;
	private byte delayTimer;
	private byte soundTimer;
	private int mostRecentInput;

	private Thread runThread;
	private Random random;

	private boolean isProgramLoaded;
	private boolean isRunning;

	public Chip8InterpreterCore() {
		this.random = new Random();
		this.vRegisters = new byte[16];
		this.ram = new byte[4096];
		this.vram = new byte[32 * 64]; // One byte per pixel
		this.input = new byte[16];

		this.addrRegister = 0;
		this.stackPointer = 0;
		this.instructionPointer = 0;
		this.stack = new int[16];
		this.mostRecentInput = 16;
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
		stackPointer = 0;
		instructionPointer = 0;
		addrRegister = 0;
		delayTimer = 0;
		soundTimer = 0;
		isProgramLoaded = false;
		isRunning = false;
	}

	// Loads font into ram from 0x000, 0x1FF
	private void loadFont(byte[] font) {
		if (font.length > 0x1FF)
			fail(ERROR_FONT_TO_BIG);

		for (int i = 0; i < font.length; i++) {
			ram[i] = font[i];
		}
	}

	private void printScreen() {
		for (int row = 0; row < 32; row++) {
			for (int column = 0; column < 8; column++) {
				for (int bit = 7; bit >= 0; bit--) {
					System.out.print(((vram[column + row * 8] >> bit) & 0x1));
				}
				System.out.print(" ");
			}
			System.out.print("\n");
		}
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
			fail(ERROR_INVALID_STACK_ACCESS);

		return stack[stackPointer];
	}

	private void pushStack(int addr) {
		stack[stackPointer] = addr;

		if (stackPointer < stack.length - 1)
			stackPointer++;
		else
			fail(ERROR_INVALID_STACK_ACCESS);
	}

	private byte getInput(int num) {
		if (num > 0xF)
			fail(ERROR_INVALID_INPUT_ACCESS);

		return input[num];
	}

	public void inputPressed(int num) {
		System.out.println("KEY PRESSED: " + String.format("0x%02X", num));
		if (num > 0xF)
			fail(ERROR_INVALID_INPUT_ACCESS);

		synchronized (input) {
			input[num] = 1;
			mostRecentInput = num;
			input.notifyAll();
		}
	}

	public void inputReleased(int num) {
		System.out.println("KEY RELEASED: " + String.format("0x%02X", num));

		if (num > 0xF)
			fail(ERROR_INVALID_INPUT_ACCESS);

		input[num] = 0;
	}

	public boolean isProgramLoaded() {
		return isProgramLoaded;
	}

	public boolean isRunning() {
		return isRunning;
	}

	public byte[] getVRAM() {
		return vram;
	}

	public boolean loadCode(byte[] code) {
		// Clear memory
		clear();

		// TL;DR lol
		if (code.length > ram.length - CHIP8_PROGLOAD_ADDR)
			return false;

		for (int i = 0; i < code.length; i++)
			ram[1 + i + CHIP8_PROGLOAD_ADDR] = code[i];

		instructionPointer = CHIP8_PROGLOAD_ADDR;

		// Load font
		loadFont(CHIP8_FONT_DATA);

		return isProgramLoaded = true;
	}

	public void dump() {
		// Print system state
		System.out.println("RAM:");
		dumpMemory(ram);
		System.out.println("\n-------------------------------------------------\n");
		System.out.println("VRAM:");
		dumpMemory(vram);
		System.out.println("\n\nImage:");
		printScreen();
		System.out.println("\n-------------------------------------------------\n");
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

				while (!exit) {
					// Stage 1: LOAD
					if (instructionPointer >= ram.length - 2)
						fail("EOF");
					byte[] opcode = new byte[] { ram[++instructionPointer], ram[++instructionPointer] };
					byte controlHigh = (byte) ((opcode[0] & 0xF0) >> 4);
					byte controlLow = (byte) (opcode[0] & 0x0F);
					byte paramHigh = (byte) ((opcode[1] & 0xF0) >> 4);
					byte paramLow = (byte) (opcode[1] & 0x0F);

					if (DEBUG_OUTPUT) {
						dumpMemory(new byte[] { controlHigh, controlLow, paramHigh, paramLow });
						System.out.println("");
					}

					// Stage 2 + 3: DECODE & EXECUTE
					switch (controlHigh) {
					case 0x0:
						switch (controlLow) {
						case 0x0:
							switch (paramHigh) {
							case 0x0:
								// NOOP operation
								if (DEBUG_OUTPUT)
									System.out.println("NOOP");
								if (paramLow != 0x0)
									fail(ERROR_INVALID_INSTRUCTION);
								break;
							case 0xE:
								switch (paramLow) {
								case 0x0: // 00E0 clear screen
									if (DEBUG_OUTPUT)
										System.out.println("Clear screen");
									clearMemory(vram);
									break;
								case 0xE: // 00EE return from subroutine
									if (DEBUG_OUTPUT)
										System.out.print("return from subroutine; pop stack (IP: " + instructionPointer
												+ " -> ");
									instructionPointer = popStack();
									if (DEBUG_OUTPUT)
										System.out.print(instructionPointer + ")");

									break;
								default:
									fail(ERROR_INVALID_INSTRUCTION);
									break;
								}
								break;
							default:
								fail(ERROR_INVALID_INSTRUCTION);
								break;
							}
							break;
						default:
							fail(ERROR_RCA_1802_UNSUPPORTED);
							break;

						}

						break;
					case 0x1: // 1NNN jump to addr NNN
						if (DEBUG_OUTPUT)
							System.out.println(
									"Jump to " + (((controlLow << 8) & 0xFF0) + ((paramHigh << 4) & 0xF0) + paramLow));
						instructionPointer = ((controlLow << 8) & 0xFF0) + ((paramHigh << 4) & 0xF0) + paramLow;
						break;
					case 0x2: // 2NNN call subroutine @ NNN
						if (DEBUG_OUTPUT)
							System.out.println("Call subroutine @"
									+ (((controlLow << 8) & 0xFF0) + ((paramHigh << 4) & 0xF0) + paramLow) + "(IP: "
									+ instructionPointer + ")");
						pushStack(instructionPointer);
						instructionPointer = ((controlLow << 8) & 0xFF0) + ((paramHigh << 4) & 0xF0) + paramLow;
						break;
					case 0x3: // 3XNN Skips the next instruction if VX equals NN
						if (DEBUG_OUTPUT)
							System.out.println("Skips instruction if VX equals NN + (VX: " + vRegisters[controlLow]
									+ (((paramHigh << 4) & 0xF0) + paramLow));
						if (vRegisters[controlLow] == ((paramHigh << 4) & 0xF0) + paramLow)
							instructionPointer += 2;
						break;
					case 0x4: // 4XNN Skips the next instruction if VX doesn't
								// equal NN
						if (DEBUG_OUTPUT)
							System.out.println("Skips instruction if VX doesn't equals NN + (VX: "
									+ vRegisters[controlLow] + (((paramHigh << 4) & 0xF0) + paramLow));
						if (vRegisters[controlLow] != ((paramHigh << 4) & 0xF0) + paramLow)
							instructionPointer += 2;
						break;
					case 0x5:
						switch (paramLow) {
						case 0x0: // 5XY0 Skips the next instruction if VX
									// equals VY
							if (DEBUG_OUTPUT)
								System.out.println("Skips next instruction if VX equals NN + (VX: " + vRegisters[controlLow]
										+ (((paramHigh << 4) & 0xF0) + paramLow));
							if (vRegisters[controlLow] == vRegisters[paramHigh])
								instructionPointer += 2;
							break;
						default:
							fail(ERROR_INVALID_INSTRUCTION);
							break;
						}
						break;
					case 0x6: // 6XNN Sets VX to NN
						vRegisters[controlLow] = (byte) (((paramHigh << 4) & 0xF0) + paramLow);
						break;
					case 0x7: // 7XNN Adds NN to VX
						vRegisters[controlLow] += (byte) (((paramHigh << 4) & 0xF0) + paramLow);
						break;
					case 0x8:
						switch (paramLow) {
						case 0x0: // 8XY0 Sets VX to the value of VY
							vRegisters[controlLow] = vRegisters[paramHigh];
							break;
						case 0x1: // 8XY1 Sets VX to VX or VY
							vRegisters[controlLow] = (byte) (vRegisters[controlLow] | vRegisters[paramHigh]);
							break;
						case 0x2: // 8XY2 Sets VX to VX and VY
							vRegisters[controlLow] = (byte) (vRegisters[controlLow] & vRegisters[paramHigh]);
							break;
						case 0x3: // 8XY3 Sets VX to VX xor VY
							vRegisters[controlLow] = (byte) (vRegisters[controlLow] ^ vRegisters[paramHigh]);
							break;
						case 0x4: // 8XY4 Adds VY to VX. VF is set to 1 when
									// there's a carry, and to 0 when there
									// isn't
							vRegisters[0xF] = 0;
							if (vRegisters[controlLow] + vRegisters[paramHigh] > Byte.MAX_VALUE)
								vRegisters[0xF] = 1;

							// TODO: unclear specification
							// vRegisters[controlLow] = (byte)
							// (vRegisters[controlLow] + vRegisters[paramHigh]);
							break;
						case 0x5: // 8XY5 VY is subtracted from VX. VF is set to
									// 0 when there's a borrow, and 1 when there
									// isn't
							vRegisters[0xF] = 1;
							if (vRegisters[controlLow] - vRegisters[paramHigh] < 0)
								vRegisters[0xF] = 0;

							// TODO: unclear specification
							// vRegisters[controlLow] = (byte)
							// (vRegisters[controlLow] - vRegisters[paramHigh]);
							break;
						case 0x6: // 8XY6 Shifts VX right by one. VF is set to
									// the value of the least significant bit of
									// VX before the shift.
							vRegisters[0xF] = (byte) (vRegisters[controlLow] & 0x1);
							vRegisters[controlLow] = (byte) (vRegisters[controlLow] >> 1);
							break;
						case 0x7: // 8XY7 Sets VX to VY minus VX. VF is set to 0
									// when there's a borrow, and 1 when there
									// isn't
							vRegisters[0xF] = 1;
							if (vRegisters[paramHigh] - vRegisters[controlLow] < 0)
								vRegisters[0xF] = 0;
							vRegisters[controlLow] = (byte) (vRegisters[paramHigh] - vRegisters[controlLow]);
							break;
						case 0xE: // 8XYE Shifts VX left by one. VF is set to
									// the value of the most significant bit of
									// VX before the shift.
							vRegisters[0xF] = (byte) (vRegisters[controlLow] & 0x80);
							vRegisters[controlLow] = (byte) (vRegisters[controlLow] << 1);
							break;
						default:
							fail(ERROR_INVALID_INSTRUCTION);
							break;
						}
						break;
					case 0x9:
						// 9XY0 Skips the next instruction if VX doesn't equal
						// VY
						if (paramLow != 0x0)
							fail(ERROR_INVALID_INSTRUCTION);
						if (vRegisters[controlLow] != vRegisters[paramHigh])
							instructionPointer += 2;
						break;
					case 0xA: // ANNN Sets I to the adress NNN
						addrRegister = ((controlLow << 8) & 0xFF0) + ((paramHigh << 4) & 0xF0) + paramLow;
						break;
					case 0xB: // BNNN Jumps to the adress NNN plus V0
						instructionPointer = ((controlLow << 8) & 0xFF0) + ((paramHigh << 4) & 0xF0) + paramLow
								+ vRegisters[0x0];
						break;
					case 0xC: // CXNN Sets VX to the result of a bitwise and
								// operation on a random number and NN
						vRegisters[controlLow] = (byte) ((((paramHigh << 4) & 0xF0) + paramLow) & random.nextInt(0xFF));
						break;
					case 0xD: // DXYN Sprites stored in memory at location in
								// index register (I), 8bits wide. Wraps around
								// the screen. If when drawn, clears a pixel,
								// register VF is set to 1 otherwise it is zero.
								// All drawing is XOR drawing (i.e. it toggles
								// the screen pixels). Sprites are drawn
								// starting at position VX, VY. N is the number
								// of 8bit rows that need to be drawn. If N is
								// greater than 1, second line continues at
								// position VX, VY+1, and so on.
						int x = vRegisters[controlLow] & 0xFF;
						int y = vRegisters[paramHigh] & 0xFF;

						vRegisters[0xF] = 0;

						// for i < height
						for (int i = 0; i < paramLow; i++) {
							// retrieve current sprite
							byte currentSprite = ram[addrRegister + i];
							for (int bitShift = 0; bitShift < 8; bitShift++) {
								// Get pixel to draw on
								int pixelToDrawOn = x + bitShift + y * 64;

								// Bigger than last pixel in our row ->
								// subtract 64 (overflow)
								if (x + bitShift > 63)
									pixelToDrawOn -= 64;

								// XOR Drawing (if they differ, flip/toggle
								// pixel)
								if (((currentSprite << bitShift) & 0x80) != 0) {
									if (vram[pixelToDrawOn] == 0) {
										vram[pixelToDrawOn] = 1;
									} else {
										vRegisters[0xF] = 1;
									}
								}
							}

						}

						break;
					case 0xE:
						if (paramHigh == 0x9 && paramLow == 0xE) {
							// EX9E Skips the next instruction if the key stored
							// in VX is pressed.
							if (getInput(vRegisters[controlLow]) == 1)
								instructionPointer += 2;
						} else if (paramHigh == 0xA && paramLow == 0x1) {
							// EXA1 Skips the next instruction if the key stored
							// in VX isn't pressed.
							if (getInput(vRegisters[controlLow]) == 0)
								instructionPointer += 2;
						} else
							fail(ERROR_INVALID_INSTRUCTION);
						break;
					case 0xF:
						switch (paramHigh) {
						case 0x0:
							if (paramLow == 0x7) {
								// FX07 Sets VX to the value of the delay timer.
								vRegisters[controlLow] = delayTimer;
							} else if (paramLow == 0xA) {
								// FX0A A key press is awaited, and then stored
								// in VX.
								synchronized (input) {
									try {
										System.out.println("Waiting for input");
										input.wait();
									} catch (InterruptedException e) {
										e.printStackTrace();
									}
								}
								vRegisters[controlLow] = (byte) mostRecentInput;

							} else
								fail(ERROR_INVALID_INSTRUCTION);
							break;
						case 0x1:
							if (paramLow == 0x5) {
								// FX15 Sets the delay timer to VX.
								delayTimer = vRegisters[controlLow];
							} else if (paramLow == 0x8) {
								soundTimer = vRegisters[controlLow];
							} else if (paramLow == 0xE) {
								// FX1E Adds VX to I.
								addrRegister += vRegisters[controlLow];
							} else
								fail(ERROR_INVALID_INSTRUCTION);
							break;
						case 0x2:
							if (paramLow == 0x9) {
								// Calculate char offset. Font starts @ 0x000
								// and takes up 5 bytes each
								addrRegister = controlLow * 5;
							} else
								fail(ERROR_INVALID_INSTRUCTION);
							break;
						case 0x3:
							if (paramLow != 0x3)
								fail(ERROR_INVALID_INSTRUCTION);
							// FX33 Stores the Binary-coded decimal
							// representation of VX, with the most significant
							// of three digits at the address in I, the middle
							// digit at I plus 1, and the least significant
							// digit at I plus 2. (In other words, take the
							// decimal representation of VX, place the hundreds
							// digit in memory at location in I, the tens digit
							// at location I+1, and the ones digit at location
							// I+2.)

							// TODO
							byte vx = vRegisters[controlLow];
							byte one = (byte) (vx % 10);
							vx /= 10;
							byte ten = (byte) (vx % 10);
							vx /= 10;
							byte hundred = (byte) (vx % 10);
							ram[addrRegister] = hundred;
							ram[addrRegister + 1] = ten;
							ram[addrRegister + 2] = one;
							break;
						case 0x5: // FX55 Stores V0 to VX in memory starting at
									// address I
							for (int i = 0; i < controlLow; i++)
								ram[addrRegister + i] = vRegisters[i];
							break;
						case 0x6: // FX65 Fills V0 to VX with values from memory
									// starting at address I
							for (int i = 0; i < controlLow; i++)
								vRegisters[i] = ram[addrRegister + i];
							break;
						default:
							fail(ERROR_INVALID_INSTRUCTION);
						}
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
