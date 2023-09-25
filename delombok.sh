#!/bin/bash

# Default values for source and destination directories
SOURCE_DIR="app/src/main/java"
DESTINATION_DIR="app/src-delomboked"
# Output file for concatenated Java source code
OUTPUT_FILE="all-in-one.java"
TARGET_FILE="./jb/DeployUtil.java"
IGNORED_FILES=("App.java" "FileToIgnore2.java")

# Check if custom source and destination directories are provided as arguments
if [ $# -ge 1 ]; then
	SOURCE_DIR="$1"
fi

if [ $# -ge 2 ]; then
	DESTINATION_DIR="$2"
fi

# Execute the Delombok command with the specified or default directories
java -jar lombok-1.18.28.jar delombok "$SOURCE_DIR" -d "$DESTINATION_DIR"

# Initialize the output file
>"$OUTPUT_FILE"

# Iterate through Java source files in the directory
find "$DESTINATION_DIR" -type f -name "*.java" | while read -r file; do
	# Get the file name without the path
	filename=$(basename "$file")
	# Check if the file should be ignored
	if [[ " ${IGNORED_FILES[@]} " =~ " $filename " ]]; then
		echo "Ignoring: $file"
	else
		echo "Processing: $file"
		# Remove import and package lines, and change "public class" to "public static class"
		sed -e '/^import\|^package/d' -e 's/public class/public static class/' -e 's/public abstract/public static abstract/' "$file" >>"$OUTPUT_FILE"

		# Add an empty line to separate concatenated code from different files
		echo "" >>"$OUTPUT_FILE"
	fi
done

echo "Concatenation and modification complete. Output file: $OUTPUT_FILE"

# Insert the content of output.java between marker lines in the target file

awk '/___insert_start___/ {print; system("cat '"$OUTPUT_FILE"'"); f=1} /___insert_end___/ {print; f=0} !f && !/___insert_end___/' "$TARGET_FILE" >temp_file
mv temp_file "$TARGET_FILE"

echo "Insertion into $TARGET_FILE complete."

appcPkiDir="../appc-pki/sb3/src/main/resources/bash"
file_name_with_extension=$(basename "$TARGET_FILE")
file_name_without_extension="${file_name_with_extension%.*}"
echo "copying to ${appcPkiDir}/$file_name_without_extension"
cp "$TARGET_FILE" "${appcPkiDir}/$file_name_without_extension"
