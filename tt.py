import cv2
from ultralytics import YOLO
import time
import base64
import firebase_admin
from firebase_admin import credentials, db, messaging

# ==============================================================================
# === CONFIGURATION ===
# ==============================================================================
SERVICE_ACCOUNT_FILE = "firebase_credentials.json"
DATABASE_URL = 'https://trunk-tech-default-rtdb.firebaseio.com/' 
FCM_TOPIC = 'elephant_alerts' 
SYSTEM_ID = "D83ADDA36241" 

MODEL_PATH = 'yolov8n.pt'                                        
VIDEO_PATH = "elephant.mp4"                         
CONFIDENCE_THRESHOLD = 0.5                                       
GPS_LATITUDE = 18.6517
GPS_LONGITUDE = 73.7651

LIVE_REFRESH_RATE = 2  

# ==============================================================================
# === INITIALIZATION ===
# ==============================================================================
try:
    cred = credentials.Certificate(SERVICE_ACCOUNT_FILE)
    firebase_admin.initialize_app(cred, {'databaseURL': DATABASE_URL})
    db_ref = db.reference(f'detection_systems/{SYSTEM_ID}')
    
    # --- STATUS UPDATE: System is now ON (Initial Status) ---
    db_ref.update({
        'detection_status': "Elephant not Detected", 
        'last_updated': time.strftime('%Y-%m-%d %H:%M:%S', time.gmtime())
    })
    print(f"✅ Firebase initialized. Status: Elephant not Detected. ID: {SYSTEM_ID}")
except Exception as e:
    print(f"❌ Initialization Error: {e}")
    exit()

model = YOLO(MODEL_PATH)
cap = cv2.VideoCapture(VIDEO_PATH)

# ==============================================================================
# === IMAGE PROCESSING ===
# ==============================================================================
def frame_to_base64(frame, size=(320, 240), quality=50):
    small_frame = cv2.resize(frame, size) 
    _, buffer = cv2.imencode('.jpg', small_frame, [cv2.IMWRITE_JPEG_QUALITY, quality])
    jpg_as_text = base64.b64encode(buffer).decode('utf-8')
    return f"data:image/jpeg;base64,{jpg_as_text}"

def send_push_notification(count):
    try:
        message = messaging.Message(
            notification=messaging.Notification(
                title="Elephant Alert!",
                body=f"High Alert: {count} elephant(s) detected near {SYSTEM_ID}."
            ),
            data={
                "system_id": SYSTEM_ID,
                "status": "Elephant Detected"
            },
            topic=FCM_TOPIC,
        )
        messaging.send(message)
    except Exception as e:
        print(f"FCM Error: {e}")

# ==============================================================================
# === MAIN LOOP ===
# ==============================================================================
max_elephants_seen = 0
last_sent_status = "Elephant not Detected"
last_live_update_time = 0

print(f"\n--- Trunk Tech Monitoring System {SYSTEM_ID} ---")

try:
    while True:
        success, frame = cap.read()
        if not success:
            cap.set(cv2.CAP_PROP_POS_FRAMES, 0)
            max_elephants_seen = 0 
            continue

        current_frame_elephants = 0
        display_frame = frame.copy()
        
        results = model.track(frame, persist=True, verbose=False, imgsz=320)

        if results[0].boxes.id is not None:
            clss = results[0].boxes.cls.cpu().numpy().astype(int)
            boxes = results[0].boxes.xyxy.cpu().numpy().astype(int)
            
            for box, cls in zip(boxes, clss):
                if model.names[cls] == 'elephant':
                    current_frame_elephants += 1
                    cv2.rectangle(display_frame, (box[0], box[1]), (box[2], box[3]), (0, 0, 255), 2)

        # 1. LIVE FOOTAGE UPDATE
        current_time = time.time()
        if current_time - last_live_update_time > LIVE_REFRESH_RATE:
            live_base64 = frame_to_base64(display_frame, size=(240, 180), quality=30)
            db_ref.update({'live_footage': live_base64})
            last_live_update_time = current_time

        # 2. DETECTION LOGIC (Status Toggle)
        is_detected = current_frame_elephants > 0
        current_status = "Elephant Detected" if is_detected else "Elephant not Detected"

        # Update peak detection image and status
        if is_detected and (current_status != last_sent_status or current_frame_elephants > max_elephants_seen):
            max_elephants_seen = max(max_elephants_seen, current_frame_elephants)
            detection_base64 = frame_to_base64(display_frame, size=(320, 240), quality=60)
            
            payload = {
                'detection_status': current_status,
                'last_image_data': detection_base64,
                'max_elephant_count': max_elephants_seen,
                'last_updated': time.strftime('%Y-%m-%d %H:%M:%S', time.gmtime()),
                'location': {'latitude': GPS_LATITUDE, 'longitude': GPS_LONGITUDE}
            }
            db_ref.update(payload)
            send_push_notification(max_elephants_seen)
            last_sent_status = current_status

        # Reset to "Elephant not Detected" when area is clear
        elif not is_detected and last_sent_status == "Elephant Detected":
            db_ref.update({
                'detection_status': "Elephant not Detected",
                'last_updated': time.strftime('%Y-%m-%d %H:%M:%S', time.gmtime())
            })
            max_elephants_seen = 0 
            last_sent_status = "Elephant not Detected"

        cv2.imshow("Trunk Tech - Monitoring Feed", display_frame)
        if cv2.waitKey(1) & 0xFF == ord('q'): break

finally:
    # --- STATUS UPDATE: System is now OFF ---
    print("\n⚠️ Shutting down... Setting status to System Offline.")
    try:
        db_ref.update({
            'detection_status': 'System Offline',
            'last_updated': time.strftime('%Y-%m-%d %H:%M:%S', time.gmtime())
        })
    except:
        pass
    cap.release()
    cv2.destroyAllWindows()