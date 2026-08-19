use near_sdk::{env, near, PanicOnDefault};

#[near(contract_state)]
#[derive(PanicOnDefault)]
pub struct FreedomControlPlane {
    bootstrap_floor: u64,
    committed_version: u64,
}

#[near]
impl FreedomControlPlane {
    #[init]
    pub fn new() -> Self {
        Self {
            bootstrap_floor: 0,
            committed_version: 0,
        }
    }

    pub fn set_bootstrap_floor(&mut self, minimum_height: u64) -> u64 {
        if minimum_height < self.bootstrap_floor {
            env::panic_str("CONTROL_PLANE_ROLLBACK");
        }
        self.bootstrap_floor = minimum_height;
        self.bootstrap_floor
    }

    pub fn get_bootstrap_floor(&self) -> u64 {
        self.bootstrap_floor
    }

    pub fn apply_mutation(&mut self, write_version: u64, force_fail: bool) -> u64 {
        if force_fail {
            env::panic_str("CONTROL_PLANE_EXECUTION_FAILED");
        }
        if write_version < self.committed_version {
            env::panic_str("CONTROL_PLANE_ROLLBACK");
        }
        self.committed_version = write_version;
        self.committed_version
    }

    pub fn get_committed_version(&self) -> u64 {
        self.committed_version
    }
}
