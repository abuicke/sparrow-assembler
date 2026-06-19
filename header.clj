(ns header
  (:import [java.nio ByteBuffer ByteOrder]
           [java.io FileOutputStream]))

(defn build-minimal-elf []
  ;; The exact size of our file: 
  ;; 64 (ELF Header) + 56 (Program Header) + 12 (Machine Code) = 132 bytes
  (let [file-size 132
        buffer (ByteBuffer/allocate file-size)]
    
    ;; Linux x86-64 requires Little Endian byte order
    (.order buffer ByteOrder/LITTLE_ENDIAN)

    ;; ==========================================
    ;; 1. THE ELF HEADER (64 Bytes)
    ;; ==========================================
    ;; Magic Number (0x7F E L F)
    (.put buffer (unchecked-byte 0x7F))
    (.put buffer (byte 0x45))
    (.put buffer (byte 0x4C))
    (.put buffer (byte 0x46))
    
    (.put buffer (byte 2))    ; 64-bit architecture
    (.put buffer (byte 1))    ; Little Endian data
    (.put buffer (byte 1))    ; ELF Version 1
    (.put buffer (byte 0))    ; Target OS ABI (System V)
    
    (.position buffer 16)     ; Skip 8 bytes of padding
    
    (.putShort buffer 2)      ; Object Type: Executable (2)
    (.putShort buffer 0x3E)   ; Machine: x86-64 (62)
    (.putInt buffer 1)        ; Version 1
    
    ;; Entry Point: Standard memory start (0x400000) + 120 bytes of headers
    (.putLong buffer 0x400078)
    
    (.putLong buffer 64)      ; Program Header starts immediately after this 64-byte header
    (.putLong buffer 0)       ; We are skipping Section Headers entirely (not needed to run)
    (.putInt buffer 0)        ; Flags
    
    (.putShort buffer 64)     ; Size of this ELF Header
    (.putShort buffer 56)     ; Size of one Program Header
    (.putShort buffer 1)      ; How many Program Headers? (Just 1)
    
    (.putShort buffer 64)     ; Size of Section Header (Dummy value)
    (.putShort buffer 0)      ; How many Section Headers? (0)
    (.putShort buffer 0)      ; Section Names Index

    ;; ==========================================
    ;; 2. THE PROGRAM HEADER (56 Bytes)
    ;; ==========================================
    (.putInt buffer 1)        ; Segment Type: PT_LOAD (1) - Load into memory
    (.putInt buffer 5)        ; Permissions: Read (4) + Execute (1) = 5
    (.putLong buffer 0)       ; Offset in file to load from (0 = load the whole file)
    
    (.putLong buffer 0x400000); Virtual Address in RAM
    (.putLong buffer 0x400000); Physical Address (ignored, but must match)
    
    (.putLong buffer file-size); How many bytes to load from file (132)
    (.putLong buffer file-size); How much RAM to allocate (132)
    (.putLong buffer 0x1000)  ; Memory Alignment

    ;; ==========================================
    ;; 3. THE MACHINE CODE PAYLOAD (12 Bytes)
    ;; ==========================================
    ;; Instruction 1: mov rax, 60 (Syscall number for sys_exit)
    (.put buffer (unchecked-byte 0xB8)) ; Opcode for 'mov eax, imm32'
    (.putInt buffer 60)                 ; The number 60 (4 bytes)
    
    ;; Instruction 2: mov rdi, 0 (Exit code 0)
    (.put buffer (unchecked-byte 0xBF)) ; Opcode for 'mov edi, imm32'
    (.putInt buffer 0)                  ; The number 0 (4 bytes)
    
    ;; Instruction 3: syscall (Trigger the kernel)
    (.put buffer (unchecked-byte 0x0F)) ; Opcode part 1
    (.put buffer (unchecked-byte 0x05)) ; Opcode part 2

    ;; Return the raw bytes
    (.array buffer)))

(defn -main []
  (let [filename "minimal_exit"]
    (println "Compiling minimal ELF binary...")
    (with-open [out (FileOutputStream. filename)]
      (.write out (build-minimal-elf)))
    (println "Done! Executable written to:" filename)))
