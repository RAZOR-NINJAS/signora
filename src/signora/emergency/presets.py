"""Emergency card message presets and urgency levels."""

from dataclasses import dataclass
from typing import Dict


@dataclass
class EmergencyPreset:
    id: str
    title: str
    icon: str
    headline: str
    body: str
    tts_broadcast: str
    color_hex: str


EMERGENCY_PRESETS: Dict[str, EmergencyPreset] = {
    "medical": EmergencyPreset(
        id="medical",
        title="MEDICAL EMERGENCY",
        icon="🚑",
        headline="I NEED IMMEDIATE MEDICAL ATTENTION",
        body="Please call an ambulance immediately. I am Deaf and cannot hear audio. Please assist or call emergency services.",
        tts_broadcast="Medical emergency. The person holding this device is deaf and needs immediate medical assistance. Please call an ambulance.",
        color_hex="#dc2626",  # Red
    ),
    "police": EmergencyPreset(
        id="police",
        title="POLICE ASSISTANCE",
        icon="👮",
        headline="I NEED POLICE ASSISTANCE / I AM IN DANGER",
        body="I require urgent police assistance. Please call the police or direct an officer here.",
        tts_broadcast="Attention. The person holding this device is deaf and requires urgent police assistance. Please call the authorities.",
        color_hex="#2563eb",  # Blue
    ),
    "fire": EmergencyPreset(
        id="fire",
        title="FIRE EVACUATION",
        icon="🔥",
        headline="FIRE EMERGENCY - ASSIST EVACUATION",
        body="There is a fire or evacuation alarm. I cannot hear audio alarms. Please guide me to the nearest emergency exit.",
        tts_broadcast="Fire evacuation emergency. The person holding this device is deaf. Please guide them to the nearest emergency exit.",
        color_hex="#ea580c",  # Orange-red
    ),
    "injury": EmergencyPreset(
        id="injury",
        title="FIRST AID / INJURED",
        icon="🩹",
        headline="I AM INJURED AND NEED FIRST AID",
        body="I have sustained an injury and require first aid or medical support. Please help me sit down and call for help.",
        tts_broadcast="First aid needed. The person holding this device has an injury and requires immediate first aid assistance.",
        color_hex="#e11d48",  # Rose
    ),
    "assist": EmergencyPreset(
        id="assist",
        title="URGENT ASSISTANCE",
        icon="🆘",
        headline="I NEED ASSISTANCE - I AM DEAF",
        body="I am Deaf / Hard of Hearing. I cannot communicate via speech. Please read the emergency card or write down questions.",
        tts_broadcast="Assistance requested. The person holding this device is deaf and needs assistance. Please view their screen.",
        color_hex="#7c3aed",  # Purple
    ),
}
