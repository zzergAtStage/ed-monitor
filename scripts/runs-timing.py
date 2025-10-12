import os
import re
import json
import csv
import sys
from datetime import datetime, timedelta

def parse_logs_and_calculate_durations(log_directory, pattern):
    """
    Parses log files in a directory, extracts 'run' durations, and calculates
    statistics for each docking station. Also counts a specific event and
    displays a progress bar during processing.

    Args:
        log_directory (str): Path to the directory containing log files.
        pattern (str): Regex pattern to match log file names.

    Returns:
        None. Creates a 'run_statistics.csv' file and prints a final count.
    """
    run_data = {}  # Dictionary to store run durations per station
    undocked_count = 0  # New counter for the specific event
    
    # Define the events of interest for a 'run'
    START_EVENT_TYPE = "Undocked"
    END_EVENT_TYPE = "Docked"
    START_STATION_NAME = "Orbital Construction Site: Schweickart Town"
    
    # 1. Get a list of all files to process
    files_to_process = [f for f in os.listdir(log_directory) if re.match(pattern, f)]
    total_files = len(files_to_process)
    
    if total_files == 0:
        print("No matching log files found.")
        return

    # 2. Iterate through files with a progress bar
    print("Processing log files...")
    files_processed_count = 0
    for filename in files_to_process:
        filepath = os.path.join(log_directory, filename)
        
        # Update progress bar
        files_processed_count += 1
        progress = (files_processed_count / total_files) * 100
        bar = '#' * int(progress / 2) + '-' * (50 - int(progress / 2))
        sys.stdout.write(f"\r[{bar}] {progress:.2f}% ({files_processed_count}/{total_files} files)")
        sys.stdout.flush()
            
        with open(filepath, 'r') as log_file:
            start_time = None
            
            for line in log_file:
                try:
                    record = json.loads(line)
                    event = record.get('event')
                    timestamp_str = record.get('timestamp')
                    station_name = record.get('StationName')
                    
                    if not timestamp_str:
                        continue

                    if event == START_EVENT_TYPE and station_name == START_STATION_NAME:
                        undocked_count += 1
                        start_time = datetime.strptime(timestamp_str, '%Y-%m-%dT%H:%M:%SZ')
                            
                    elif event == END_EVENT_TYPE:
                        if start_time is not None:
                            if station_name and station_name != START_STATION_NAME:
                                end_time = datetime.strptime(timestamp_str, '%Y-%m-%dT%H:%M:%SZ')
                                duration = (end_time - start_time).total_seconds()
                                
                                destination_station = station_name
                                
                                if destination_station not in run_data:
                                    run_data[destination_station] = []
                                
                                run_data[destination_station].append(duration)
                                
                                start_time = None

                except json.JSONDecodeError:
                    print(f"\nError: Failed to parse JSON in file {filename}: {line.strip()}")
                    continue
                except ValueError as e:
                    print(f"\nError: Timestamp parsing failed in file {filename}: {e}")
                    continue

    sys.stdout.write("\n")
    
    final_statistics = []
    for station, durations in run_data.items():
        if not durations:
            continue

        avg_duration_all = sum(durations) / len(durations)
        filtered_durations = [d for d in durations if abs(d - avg_duration_all) <= 0.5 * avg_duration_all]
        skipped_runs = len(durations) - len(filtered_durations)
        
        if not filtered_durations:
            final_statistics.append({
                'Station': station,
                'Amounts of runs': 0,
                'Average time (seconds)': 0.0,
                'Skipped runs': skipped_runs
            })
            continue
            
        amount_of_runs = len(filtered_durations)
        average_time = sum(filtered_durations) / amount_of_runs
        
        final_statistics.append({
            'Station': station,
            'Amounts of runs': amount_of_runs,
            'Average time (seconds)': average_time,
            'Skipped runs': skipped_runs
        })

    output_filename = 'run_statistics.csv'
    if final_statistics:
        with open(output_filename, 'w', newline='') as csvfile:
            fieldnames = ['Station', 'Amounts of runs', 'Average time (seconds)', 'Skipped runs']
            writer = csv.DictWriter(csvfile, fieldnames=fieldnames)
            writer.writeheader()
            writer.writerows(final_statistics)
        print(f"Successfully created {output_filename} with the run statistics.")
    else:
        print("No valid runs were found to generate a statistics file.")
        
    print(f"\nTotal 'Undocked' events from '{START_STATION_NAME}': {undocked_count}")


if __name__ == '__main__':
    # --- Configuration ---
    LOG_DIR = '.' 
    FILE_PATTERN = r'^(log|Journal\.\d{4}-\d{2}-\d{2}T\d{6}\.\d{2}\.log)$'
    # ---------------------

    # DUMMY FILE CREATION (for testing)
    # >>> REMOVE THIS ENTIRE BLOCK WHEN YOU WANT TO RUN ON YOUR REAL LOG FILES <<<
    if not os.path.exists(os.path.join(LOG_DIR, 'test_log_1.json')) and \
       not os.path.exists(os.path.join(LOG_DIR, 'test_log_2.json')):
        with open(os.path.join(LOG_DIR, 'test_log_1.json'), 'w') as f:
            f.write('{"timestamp": "2025-08-02T12:05:00Z", "event": "Undocked", "StationName": "Orbital Construction Site: Schweickart Town"}\n')
            f.write('{"timestamp": "2025-08-02T12:10:00Z", "event": "Docked", "StationName": "Alpha Colony"}\n')
            f.write('{"timestamp": "2025-08-02T12:15:00Z", "event": "Undocked", "StationName": "Orbital Construction Site: Schweickart Town"}\n')
            f.write('{"timestamp": "2025-08-02T12:20:00Z", "event": "Docked", "StationName": "Alpha Colony"}\n')
        with open(os.path.join(LOG_DIR, 'test_log_2.json'), 'w') as f:
            f.write('{"timestamp": "2025-08-02T12:25:00Z", "event": "Undocked", "StationName": "Orbital Construction Site: Schweickart Town"}\n')
            f.write('{"timestamp": "2025-08-02T12:30:00Z", "event": "Docked", "StationName": "Beta Outpost"}\n')
            f.write('{"timestamp": "2025-08-02T12:35:00Z", "event": "Undocked", "StationName": "Orbital Construction Site: Schweickart Town"}\n')
            f.write('{"timestamp": "2025-08-02T12:55:00Z", "event": "Docked", "StationName": "Beta Outpost"}\n')
    
    # Process the files using the configured pattern
    parse_logs_and_calculate_durations(LOG_DIR, FILE_PATTERN)