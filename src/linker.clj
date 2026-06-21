(ns linker
  (:import [java.nio ByteBuffer ByteOrder]
           [java.io FileOutputStream]))

;;
;; typedef struct {
;;     unsigned char e_ident[16]; /* ELF identification bytes (Magic, Class, Endianness, etc.) */
;;     Elf64_Half    e_type;      /* Object file type (e.g., ET_REL, ET_EXEC, ET_DYN) */
;;     Elf64_Half    e_machine;   /* Target architecture / Machine type */
;;     Elf64_Word    e_version;   /* Object file version */
;;     Elf64_Addr    e_entry;     /* Entry point virtual address */
;;     Elf64_Off     e_phoff;     /* Program header table file offset */
;;     Elf64_Off     e_shoff;     /* Section header table file offset */
;;     Elf64_Word    e_flags;     /* Processor-specific flags */
;;     Elf64_Half    e_ehsize;    /* ELF header size in bytes */
;;     Elf64_Half    e_phentsize; /* Program header table entry size */
;;     Elf64_Half    e_phnum;     /* Number of program header entries */
;;     Elf64_Half    e_shentsize; /* Section header table entry size */
;;     Elf64_Half    e_shnum;     /* Number of section header entries */
;;     Elf64_Half    e_shstrndx;  /* Section header string table index */
;; } Elf64_Ehdr;
;; 
;; 
;; typedef struct
;; {
;;   Elf64_Word    p_type;    /* Segment type */
;;   Elf64_Word    p_flags;   /* Segment flags */
;;   Elf64_Off     p_offset;  /* Segment file offset */
;;   Elf64_Addr    p_vaddr;   /* Segment virtual address */
;;   Elf64_Addr    p_paddr;   /* Segment physical address */
;;   Elf64_Xword   p_filesz;  /* Segment size in file */
;;   Elf64_Xword   p_memsz;   /* Segment size in memory */
;;   Elf64_Xword   p_align;   /* Segment alignment */
;; } Elf64_Phdr;
;; 
(defn build-minimal-elf []
  ;; The exact size of the file: 
  ;; 64 (ELF Header) + 56 (Program Header) + 44 (Machine Code) = 164 bytes
  (let [file-size 164
        buffer (ByteBuffer/allocate file-size)]
    
    ;; Linux x86-64 requires Little Endian byte order
    (.order buffer ByteOrder/LITTLE_ENDIAN)

    ;; ==========================================
    ;; 1. THE ELF HEADER (64 Bytes)
    ;; ==========================================
    ;;
    ;; = = = = = = = = = = = = = = = = = = = = =
    ;; ELF Identification, e_ident[16]
    ;;
    ;; Magic Number (0x7F E L F)
    (.put buffer (byte 0x7F))
    (.put buffer (byte 0x45))
    (.put buffer (byte 0x4C))
    (.put buffer (byte 0x46))
    
    (.put buffer (byte 2))    ; 64-bit architecture
    (.put buffer (byte 1))    ; Little Endian data
    (.put buffer (byte 1))    ; ELF Version 1
    (.put buffer (byte 0))    ; Target OS ABI (System V)
    
    (.position buffer 16)     ; Skip 8 bytes of padding
    ;; = = = = = = = = = = = = = = = = = = = = =
    
    (.putShort buffer 2)      ; Object Type: Executable (2), e_type
    (.putShort buffer 0x3E)   ; Machine: x86-64 (62), e_machine
    (.putInt buffer 1)        ; Version 1, e_version
    
    (.putLong buffer 0x400078); Entry Point: 0x400000 (Linux base address) + 120 bytes of headers, e_entry
    
    (.putLong buffer 64)      ; Program Header starts immediately after this 64-byte header, e_phoff
    (.putLong buffer 0)       ; We are skipping Section Headers entirely (not needed to run), e_shoff
    (.putInt buffer 0)        ; Flags, e_flags
    
    (.putShort buffer 64)     ; Size of this ELF Header, e_ehsize
    (.putShort buffer 56)     ; Size of one Program Header, e_phentsize
    (.putShort buffer 1)      ; How many Program Headers? (Just 1), e_phnum
    
    (.putShort buffer 64)     ; Size of Section Header, e_shentsize
    (.putShort buffer 0)      ; How many Section Headers? (0), e_shnum
    (.putShort buffer 0)      ; Section Names Index, e_shstrndx

    ;; ==========================================
    ;; 2. THE PROGRAM HEADER (56 Bytes)
    ;; ==========================================
    (.putInt buffer 1)        ; Segment Type: PT_LOAD (1) - Load into memory, p_type
    (.putInt buffer 5)        ; Permissions: Read (4) + Execute (1) = 5, p_flags
    (.putLong buffer 0)       ; Offset in file to load from (0 = load the whole file), p_offset
    
    (.putLong buffer 0x400000); Virtual Address in RAM, p_vaddr
    (.putLong buffer 0x400000); Physical Address (ignored, but must match), p_paddr
    
    (.putLong buffer file-size); How many bytes to load from file (164), p_filesz
    (.putLong buffer file-size); How much RAM to allocate (164), p_memsz
    (.putLong buffer 0x1000)  ; Memory Alignment, p_align

    ;; ==========================================
    ;; 3. THE MACHINE CODE PAYLOAD (44 Bytes)
    ;; ==========================================
    
    ;; Instruction 1: mov eax, 0x0A00 (Initialize with newline '\n' in the second byte)
    (.put buffer (unchecked-byte 0xB8)) 
    (.putInt buffer 0x0A00)
    
    ;; Instruction 2: add al, 2 (Add 2 to the lowest byte)
    (.put buffer (unchecked-byte 0x04))
    (.put buffer (unchecked-byte 0x02))

    ;; Instruction 3: add al, 3 (Add 3 to the lowest byte)
    (.put buffer (unchecked-byte 0x04))
    (.put buffer (unchecked-byte 0x03))

    ;; Instruction 4: add al, 48 (Convert the sum '5' to ASCII)
    (.put buffer (unchecked-byte 0x04))
    (.put buffer (unchecked-byte 0x30))

    ;; Instruction 5: push rax (Push result to stack to get a memory pointer for sys_write)
    (.put buffer (unchecked-byte 0x50))

    ;; Instruction 6: mov edi, 1 (File descriptor 1: stdout)
    (.put buffer (unchecked-byte 0xBF))
    (.putInt buffer 1)

    ;; Instruction 7: mov rsi, rsp (Set string buffer pointer to the top of the stack)
    (.put buffer (unchecked-byte 0x48))
    (.put buffer (unchecked-byte 0x89))
    (.put buffer (unchecked-byte 0xE6))

    ;; Instruction 8: mov edx, 2 (String length: '5' and '\n' = 2 bytes)
    (.put buffer (unchecked-byte 0xBA))
    (.putInt buffer 2)

    ;; Instruction 9: mov eax, 1 (Syscall number for sys_write)
    (.put buffer (unchecked-byte 0xB8))
    (.putInt buffer 1)

    ;; Instruction 10: syscall (Trigger the kernel to print)
    (.put buffer (unchecked-byte 0x0F))
    (.put buffer (unchecked-byte 0x05))

    ;; Instruction 11: mov edi, 0 (Exit code 0)
    (.put buffer (unchecked-byte 0xBF))
    (.putInt buffer 0)

    ;; Instruction 12: mov eax, 60 (Syscall number for sys_exit)
    (.put buffer (unchecked-byte 0xB8))
    (.putInt buffer 60)

    ;; Instruction 13: syscall (Trigger the kernel to exit)
    (.put buffer (unchecked-byte 0x0F))
    (.put buffer (unchecked-byte 0x05))

    ;; Return the raw bytes
    (.array buffer)))

(defn -main []
  (let [filename "math_elf"]
    (println "Compiling minimal ELF binary...")
    (with-open [out (FileOutputStream. filename)]
      (.write out (build-minimal-elf)))
    (println "Done! Executable written to:" filename)))
