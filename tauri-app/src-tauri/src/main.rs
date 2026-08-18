#![cfg_attr(not(debug_assertions), windows_subsystem = "windows")]

mod commands;
mod process_supervisor;

use process_supervisor::ProcessSupervisorState;

fn main() {
    commands::run::<ProcessSupervisorState>();
}
