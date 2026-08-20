use near_sdk::json_types::U64;
use near_sdk::{near, PanicOnDefault};

#[near(contract_state)]
#[derive(PanicOnDefault)]
pub struct FreedomNearProbe {
    version: u64,
}

#[near]
impl FreedomNearProbe {
    #[init]
    pub fn new(initial_version: U64) -> Self {
        Self {
            version: initial_version.0,
        }
    }

    pub fn get_version(&self) -> U64 {
        U64(self.version)
    }

    pub fn set_version(&mut self, version: U64) {
        self.version = version.0;
    }

    pub fn set_version_mismatch(&mut self, requested_version: U64) {
        self.version = requested_version.0.saturating_add(1);
    }

    pub fn fail_write(&mut self, _version: U64) {
        near_sdk::env::panic_str("INTENTIONAL_L3_EXECUTION_FAILURE");
    }
}
