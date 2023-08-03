#!/bin/bash

X_TOBE_CLIENT_SECRET="{{X-TOBE-CLIENT-SECRET}}"
SERVER_ROOT_URI="{{SERVER-ROOT-URI}}"
THIS_DEPLOYMENT_ID="{{THIS-DEPLOYMENT-ID}}"
THIS_DEPLOY_DEFINITION_ID="{{THIS-DEPLOY-DEFINITION-ID}}"

# Function to upload file to azure blob storage
# Usage: upload_to_azure <file_path> <file_name> <x_tobe_client_secret>  <expire_in_minutes>  <server_root>
upload_to_azure() {
	local file_name=${2:-$1}
	local x_tobe_client_secret=${3:-$X_TOBE_CLIENT_SECRET}
	local expire_in_minutes=${4:-10}
	local server_root=${5:-$SERVER_ROOT_URI}
	local upload_url=$(curl -H "X-TOBE-CLIENT-SECRET: ${x_tobe_client_secret}" "${server_root}/sapi/upload-url?fileName=${file_name}&savedAt=azureblob&expireInMinutes=${expire_in_minutes}&deploymentId=${THIS_DEPLOYMENT_ID}")
	curl -v -X PUT -H "x-ms-blob-type: BlockBlob" --data-binary "${1}" "${upload_url}"
	local blob_id=$(echo "$upload_url" | grep -oE "[^/]+/\S+" | cut -d '?' -f1 | awk -F '/' '{print $NF}')
	curl -H "X-TOBE-CLIENT-SECRET: ${x_tobe_client_secret}" "${server_root}/sapi/upload-url?blobId=${blob_id}"
}

# Function to download file
# Usage: download_asset <asset_id> <file_name> <x_tobe_client_secret> <server_root>

download_asset() {
	local file_name=${2:-$1}
	local x_tobe_client_secret=${3:-$X_TOBE_CLIENT_SECRET}
	local server_root=${4:-$SERVER_ROOT_URI}
	curl -v -X GET -L -o ${file_name} -H "X-TOBE-CLIENT-SECRET: ${x_tobe_client_secret}" "${server_root}/sapi/asset-download/${1}"
}

# Function to download file in deployment_downloads.txt
# Usage: download_assets_list <target_dir> <file_name>
download_assets_list() {
	# Define the target directory
	local target_dir=${1:-$PWD}
	# Specify the name of files to search for
	local file_name=${2:-"deployment_downloads.txt"}
	# Find all files under the target directory with the specified name
	find "$target_dir" -type f -name "$file_name" | while IFS= read -r file; do
		echo "Processing file: $file"
		# Read the content of the file into an array
		readarray -t lines <"$file"
		# Iterate over each line in the file
		for line in "${lines[@]}"; do
			echo "Processing line: $line"
			# Split the line by commas, at most 3 times
			IFS=, read -r col1 col2 col3 rest <<<"$line"
			# Check if the array has exactly 3 elements
			if [ -n "$col1" ] && [ -n "$col2" ] && [ -n "$col3" ] && [ -z "$rest" ]; then
				# Do something with the split columns (col1, col2, col3)
				echo "Column 1: $col1"
				echo "Column 2: $col2"
				echo "Column 3: $col3"
				download_asset "$col1" "$col2"
			else
				# Skip processing this line
				echo "Skipping line: $line (Cannot split into three parts)"
			fi
		done
	done
}

# Function to unzip a file and keep the extracted files in the same directory as the zip file.
# Will skip the operation if the target directory already contains files from the zip.
unzip_keep_in_same_directory() {
	# Check if the user provided the zip file as an argument
	if [ $# -lt 1 ]; then
		echo "Usage: unzip_keep_in_same_directory <zip_file>"
		return 1
	fi

	# Get the absolute path of the zip file
	zip_file=$(realpath "$1")
	if [ ! -f "$zip_file" ]; then
		echo "Error: Zip file not found!"
		return 1
	fi

	# Get the directory path of the zip file
	zip_dir=$(dirname "$zip_file")

	# Get the current working directory
	current_dir=$(pwd)

	# Change to the zip directory to unzip the file there
	cd "$zip_dir" || return 1

	# Check if there are overlapping files
	overlapping_files=$(unzip -Z1 "$zip_file" | xargs -I{} sh -c '[ -e "{}" ] && echo "{}"')
	if [ -n "$overlapping_files" ]; then
		echo "The following files already exist in the target directory:"
		echo "$overlapping_files"
	fi

	shift
	unzip "$@" "$zip_file"

	# Return to the original directory
	cd "$current_dir" || return 1
}

retrieve_setting_value() {
	# Read JSON data from the fixed file name (e.g., definition.json)
	value=$(cat definition.json | jq .settings | jq -j | jq -r "$1")
	# Return the result
	echo "$value"
}