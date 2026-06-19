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
  ;; 64 (ELF Header) + 56 (Program Header) + 12 (Machine Code) = 132 bytes
  (let [file-size 132
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
    
    (.putLong buffer file-size); How many bytes to load from file (132), p_filesz
    (.putLong buffer file-size); How much RAM to allocate (132), p_memsz
    (.putLong buffer 0x1000)  ; Memory Alignment, p_align

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
