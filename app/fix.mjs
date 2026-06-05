import fs from 'fs';

const file = '/app/src/main/java/com/example/ui/chat/ChatScreen.kt';
const content = fs.readFileSync(file, 'utf8');
const lines = content.split('\n');

// 3601 is line 3602 (0-indexed) 
console.log("Line 3602:", lines[3601]);
console.log("Line 4760:", lines[4759]);

if (lines[3601].includes('if (isOnlineMode) {') && lines[4759].includes('    }')) {
    const num_lines_to_remove = 4760 - 3602 + 1;
    lines.splice(3601, num_lines_to_remove);
    fs.writeFileSync(file, lines.join('\n'));
    console.log("Successfully removed " + num_lines_to_remove + " lines.");
} else {
    console.log("Validation failed, did not modify file.");
}
